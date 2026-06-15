<#
.SYNOPSIS
    P0 审计脚本 - 项目结构重构前置依赖扫描

.DESCRIPTION
    在启动 P0.5-P8 任何批次前执行。产出 5 份依赖清单，作为整个重构的基线附件。
    执行后人工审阅输出，确认没有遗漏的隐式依赖。

.PARAMETER OutputDir
    输出目录，默认 docs/refactor-audit

.EXAMPLE
    .\scripts\refactor-p0-audit.ps1
    .\scripts\refactor-p0-audit.ps1 -OutputDir docs\my-audit
#>

param(
    [string]$OutputDir = "docs\refactor-audit"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

# ── PATH 引导：确保 rg 可被发现 ──
# pwsh -File 不总是继承交互 shell 的 PATH；rg 常见安装位置如下。
$rgCandidates = @(
    "$env:LOCALAPPDATA\mimicode\bin",
    "$env:LOCALAPPDATA\Qoder\bin",
    "$env:USERPROFILE\.local\bin",
    "$env:USERPROFILE\.cargo\bin"
)
foreach ($p in $rgCandidates) {
    if ($p -and (Test-Path -LiteralPath (Join-Path $p 'rg.exe'))) {
        if ($env:PATH -notlike "*$p*") {
            $env:PATH = "$p;$env:PATH"
        }
    }
}
if (-not (Get-Command rg -ErrorAction SilentlyContinue)) {
    Write-Host "[FATAL] rg (ripgrep) not found in PATH. Install it or add its location to PATH." -ForegroundColor Red
    Pop-Location
    exit 2
}

# 确保输出目录存在
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $OutputDir))) {
    New-Item -ItemType Directory -Force -Path (Join-Path $repoRoot $OutputDir) | Out-Null
}

function Write-Report {
    param([string]$Name, [string[]]$Lines)
    $path = Join-Path $repoRoot "$OutputDir\$Name"
    # 项目约定：UTF-8 无 BOM；Out-File -Encoding UTF8 在 PS 5.1 写 BOM，改用 .NET API
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines($path, $Lines, $utf8NoBom)
    Write-Host "[OK] $Name ($($Lines.Count) lines)" -ForegroundColor Green
}

Write-Host "=== P0 Audit: scanning from $repoRoot ===" -ForegroundColor Cyan
Push-Location $repoRoot

# ── 报告 1: ShuLiAppContainer 依赖清单 ──
# 手写 DI 容器。任何类被它注入，移动后必须同步这里的 import。
$containerPath = "app/src/main/java/com/shuli/reader/core/ShuLiAppContainer.kt"
if (Test-Path -LiteralPath $containerPath) {
    $containerContent = Get-Content -LiteralPath $containerPath -Encoding UTF8
    $imports = $containerContent | Where-Object { $_ -match "^import com\.shuli\.reader\." }
    $report = @(
        "# ShuLiAppContainer.kt import 清单",
        "# 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
        "# 任何被此容器引用的类，移动后必须同步修改此文件 import",
        "# 共 $($imports.Count) 条",
        ""
    ) + $imports
    Write-Report "01-shuliappcontainer-imports.txt" $report
} else {
    Write-Host "[WARN] $containerPath not found" -ForegroundColor Yellow
}

# ── 报告 2: MainActivity 依赖清单 ──
$mainPath = "app/src/main/java/com/shuli/reader/MainActivity.kt"
if (Test-Path -LiteralPath $mainPath) {
    $mainContent = Get-Content -LiteralPath $mainPath -Encoding UTF8
    $imports = $mainContent | Where-Object { $_ -match "^import com\.shuli\.reader\." }
    $report = @(
        "# MainActivity.kt import 清单",
        "# 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
        "# 顶层入口。移动被引用类时必须同步此文件",
        "# 共 $($imports.Count) 条",
        ""
    ) + $imports
    Write-Report "02-mainactivity-imports.txt" $report
} else {
    Write-Host "[WARN] $mainPath not found" -ForegroundColor Yellow
}

# ── 报告 3: 导航入口扫描 ──
# 查找 NavController / NavHost / composable("route") / navigate( 等模式
# 任何移动 UI 页面时必须同步导航注册点
$navPatterns = @(
    'composable\s*\(\s*"[^"]+"\s*\)',
    'NavController',
    'NavHost',
    'navigate\(',
    'NavGraphBuilder'
)
$navFiles = @()
foreach ($pat in $navPatterns) {
    $matches = rg -n $pat "app/src/main/java" 2>$null
    if ($matches) { $navFiles += $matches }
}
$report = @(
    "# 导航入口扫描",
    "# 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "# 模式: $($navPatterns -join ' | ')",
    "# 任何 Screen 移动时必须同步这些注册点",
    ""
) + ($navFiles | Sort-Object -Unique)
Write-Report "03-navigation-entrypoints.txt" $report

# ── 报告 4: ReadingStatus 调用点清单 ──
# §1.5 标记的跨域使用模型，第一阶段不动，但需要提前建立调用点清单
$readingStatusRefs = rg -l "ReadingStatus" "app/src/main/java" 2>$null
$report = @(
    "# ReadingStatus 调用点清单",
    "# 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "# §1.5 标记：跨 bookshelf/reader/stats/repository 使用，第一阶段保留",
    "# 后期迁入 core/data/model 时，本清单即为修改范围",
    ""
) + $readingStatusRefs
Write-Report "04-readingstatus-callers.txt" $report

# ── 报告 5: 测试目录结构镜像度 ──
# 检查 app/src/test 与 app/src/androidTest 的包结构是否与 main 镜像
# 不镜像的测试在 P1-P8 移动时可能只需改 import，不必移动文件
$testDirs = @(
    "app/src/test/java/com/shuli/reader",
    "app/src/androidTest/java/com/shuli/reader"
)
$report = @(
    "# 测试目录结构",
    "# 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "# 用于判断 P1-P8 移动时测试文件是否需要同步移动（vs 只改 import）",
    ""
)
foreach ($d in $testDirs) {
    if (Test-Path -LiteralPath $d) {
        $report += "=== $d ==="
        $subdirs = Get-ChildItem -LiteralPath $d -Directory -Recurse |
            ForEach-Object { $_.FullName.Substring((Get-Item -LiteralPath $d).FullName.Length + 1).Replace('\','/') }
        $report += $subdirs
        $report += ""
    } else {
        $report += "[MISSING] $d"
    }
}
Write-Report "05-test-directory-layout.txt" $report

# ── 报告 6: 当前文件计数基线 ──
# §9 量化标准的基线值。重构后对比判断是否达标
$featureReaderRoot = "app/src/main/java/com/shuli/reader/feature/reader"
$rootKtFiles = if (Test-Path -LiteralPath $featureReaderRoot) {
    (Get-ChildItem -LiteralPath $featureReaderRoot -File -Filter "*.kt").Count
} else { 0 }

$viewModelPath = Join-Path $featureReaderRoot "ReaderViewModel.kt"
$vmImportCount = 0
if (Test-Path -LiteralPath $viewModelPath) {
    $vmContent = Get-Content -LiteralPath $viewModelPath -Encoding UTF8
    $vmImportCount = ($vmContent | Where-Object { $_ -match "^import " }).Count
}

$v5Dir = Join-Path $featureReaderRoot "component/quicksettings/v5"
$v5Exists = Test-Path -LiteralPath $v5Dir
$oldQsDir = Join-Path $featureReaderRoot "component/quicksettings"
$oldQsFiles = if (Test-Path -LiteralPath $oldQsDir) {
    (Get-ChildItem -LiteralPath $oldQsDir -File -Filter "*.kt" |
        Where-Object { $_.Name -notmatch "^v5$" }).Count
} else { 0 }

$report = @(
    "# 重构基线计数",
    "# 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "# §9 量化标准对照用",
    "",
    "feature/reader/ 根目录 .kt 文件数:  $rootKtFiles  (目标 < 5)",
    "ReaderViewModel.kt import 行数:     $vmImportCount  (目标 < 70)",
    "component/quicksettings/v5/ 存在:   $v5Exists  (目标 False)",
    "component/quicksettings/ 旧文件数:  $oldQsFiles  (目标 0)",
    "",
    "V5 / QuickSettingsSheet / SETTINGS_PANEL_V5_ENABLED 命中数:",
    "  (P0.5 完成后应全部为 0)"
)

$v5hits = (rg -c "\b\w+V5\b" "app/src/main/java" "app/src/test" "app/src/androidTest" 2>$null | Measure-Object).Count
$qshits = (rg -c "QuickSettingsSheet" "app/src/main/java" "app/src/test" "app/src/androidTest" 2>$null | Measure-Object).Count
$flaghits = (rg -c "SETTINGS_PANEL_V5_ENABLED" "app/src/main/java" "app/src/test" "app/src/androidTest" 2>$null | Measure-Object).Count
$report += "  V5 后缀类名/文件名命中:  $v5hits"
$report += "  QuickSettingsSheet 命中:  $qshits"
$report += "  SETTINGS_PANEL_V5_ENABLED: $flaghits"
Write-Report "06-baseline-counts.txt" $report

Pop-Location
Write-Host ""
Write-Host "=== P0 Audit 完成 ===" -ForegroundColor Green
Write-Host "输出目录: $(Join-Path $repoRoot $OutputDir)" -ForegroundColor Cyan
Write-Host "下一步: 人工审阅 6 份报告，确认无遗漏后启动 P0.5" -ForegroundColor Cyan
