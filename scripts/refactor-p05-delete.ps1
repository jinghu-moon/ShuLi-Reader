<#
.SYNOPSIS
    P0.5 删除确认清单 - 旧 quicksettings 死代码清理

.DESCRIPTION
    本脚本按 §4 P0.5 的 9 个删除候选项逐项执行：
      1. 预检查（引用计数、测试影响）
      2. 人工确认
      3. 执行删除 + 编译验证
      4. 失败自动回滚到本项起点

    全部 9 项通过 = P0.5 完成，可启动 P1。
    任意一项 3 次修复仍失败 = 整个 P0.5 中止，保留所有 tag 供事后诊断。

.PARAMETER DryRun
    只预检查不实际删除。用于第一次跑，看清全部影响。

.PARAMETER SkipCompile
    跳过每批后的 gradle compile（仅用于调试脚本本身）。

.PARAMETER StartFrom
    从第 N 项开始（用于续跑）。默认 1。

.EXAMPLE
    .\scripts\refactor-p05-delete.ps1 -DryRun
    .\scripts\refactor-p05-delete.ps1
    .\scripts\refactor-p05-delete.ps1 -StartFrom 5
#>

param(
    [switch]$DryRun,
    [switch]$SkipCompile,
    [int]$StartFrom = 1
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot

# ── PATH 引导：确保 rg 可被发现 ──
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

# ── 工具函数 ──

function Count-References {
    param([string]$Symbol)
    $hits = rg -c "\b$([regex]::Escape($Symbol))\b" `
        "app/src/main/java" "app/src/test" "app/src/androidTest" 2>$null
    return @($hits).Count
}

function Compile-Debug {
    if ($SkipCompile) {
        Write-Host "  [SKIP] compile (per -SkipCompile)" -ForegroundColor Yellow
        return $true
    }
    Write-Host "  [COMPILE] ./gradlew :app:compileDebugKotlin" -ForegroundColor Cyan
    $output = ./gradlew.bat :app:compileDebugKotlin 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Write-Host "  [FAIL] compile exit code $exitCode" -ForegroundColor Red
        $output | Select-Object -Last 20 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        return $false
    }
    Write-Host "  [OK] compile passed" -ForegroundColor Green
    return $true
}

function Git-SafeDelete {
    param([string]$Path)
    if (Test-Path -LiteralPath $Path) {
        git rm -f $Path 2>$null | Out-Null
        if (-not (Test-Path -LiteralPath $Path)) {
            Write-Host "    deleted: $Path" -ForegroundColor DarkGray
            return $true
        } else {
            Write-Host "    FAILED to delete: $Path" -ForegroundColor Red
            return $false
        }
    } else {
        Write-Host "    [NOT FOUND] $Path (already gone)" -ForegroundColor Yellow
        return $true
    }
}

function Git-SafeDeleteDir {
    param([string]$Path)
    if (Test-Path -LiteralPath $Path) {
        git rm -rf $Path 2>$null | Out-Null
        if (-not (Test-Path -LiteralPath $Path)) {
            Write-Host "    deleted dir: $Path" -ForegroundColor DarkGray
            return $true
        } else {
            Write-Host "    FAILED to delete dir: $Path" -ForegroundColor Red
            return $false
        }
    } else {
        Write-Host "    [NOT FOUND] $Path (already gone)" -ForegroundColor Yellow
        return $true
    }
}

function Prompt-Continue {
    param([string]$Message)
    if ($DryRun) {
        Write-Host "  [DRY-RUN] would: $Message" -ForegroundColor Yellow
        return $true
    }
    $answer = Read-Host "  $Message [y/N]"
    return ($answer -eq 'y' -or $answer -eq 'Y')
}

# ── 主流程 ──

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$overallTag = "refactor-p05-start-$timestamp"

if (-not $DryRun) {
    git tag $overallTag
    Write-Host "[TAG] $overallTag (P0.5 overall rollback point)" -ForegroundColor Cyan
} else {
    Write-Host "[DRY-RUN] would tag: $overallTag" -ForegroundColor Yellow
}

# 候选清单：每项 = (名称, 类型, 路径/符号, 预期引用数, 处理函数)
$candidates = @(
    @{
        Id = 1
        Name = "删除 QuickSettingsSheet.kt"
        Kind = "file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/component/QuickSettingsSheet.kt"
        Symbol = "QuickSettingsSheet"
        PreCheckDesc = "引用它的只有 ReaderOverlayPanels.kt（旧版分支）。P0.5 同步删除分支后应 0 命中。"
        ExpectedRefsAfterBranchRemoval = 0
    },
    @{
        Id = 2
        Name = "删除 HeaderFooterCustomizationPanel.kt"
        Kind = "file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/component/quicksettings/HeaderFooterCustomizationPanel.kt"
        Symbol = "HeaderFooterCustomizationPanel"
        PreCheckDesc = "仅被旧 SettingsPanel 引用。旧 SettingsPanel 同步删除后应 0 命中。"
        ExpectedRefsAfterBranchRemoval = 0
    },
    @{
        Id = 3
        Name = "删除 InteractionPanel.kt"
        Kind = "file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/component/quicksettings/InteractionPanel.kt"
        Symbol = "InteractionPanel"
        PreCheckDesc = "仅被旧 QuickSettingsSheet 引用。"
        ExpectedRefsAfterBranchRemoval = 0
    },
    @{
        Id = 4
        Name = "删除 LayoutPanel.kt"
        Kind = "file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/component/quicksettings/LayoutPanel.kt"
        Symbol = "LayoutPanel"
        PreCheckDesc = "仅被旧 QuickSettingsSheet 引用。"
        ExpectedRefsAfterBranchRemoval = 0
    },
    @{
        Id = 5
        Name = "删除 SettingsPanel.kt (旧)"
        Kind = "file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/component/quicksettings/SettingsPanel.kt"
        Symbol = "quicksettings\.SettingsPanel\b"
        PreCheckDesc = "仅被旧 QuickSettingsSheet 引用。注意与 v5/SettingsPanelV5 区分。"
        ExpectedRefsAfterBranchRemoval = 0
    },
    @{
        Id = 6
        Name = "删除 SharedComponents.kt"
        Kind = "file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/component/quicksettings/SharedComponents.kt"
        Symbol = "quicksettings\.SharedComponents\b"
        PreCheckDesc = "被旧 quicksettings 多文件引用，可能也被 v5 引用。若 v5 引用则先迁出共享函数再删。"
        ExpectedRefsAfterBranchRemoval = -1  # -1 表示"需要人工判断"
    },
    @{
        Id = 7
        Name = "删除 StylePanel.kt"
        Kind = "file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/component/quicksettings/StylePanel.kt"
        Symbol = "StylePanel"
        PreCheckDesc = "仅被旧 QuickSettingsSheet 引用。"
        ExpectedRefsAfterBranchRemoval = 0
    },
    @{
        Id = 8
        Name = "删除 ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED"
        Kind = "field"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/settings/ReaderFeatureFlags.kt"
        Symbol = "SETTINGS_PANEL_V5_ENABLED"
        PreCheckDesc = "引用点：ReaderOverlayPanels.kt (旧版分支)、ReaderFeatureFlagsTest.kt。分支删除 + 测试断言删除后应 0 命中。"
        ExpectedRefsAfterBranchRemoval = 0
        ExtraActions = @(
            "删除 ReaderOverlayPanels.kt 中 'if (SETTINGS_PANEL_V5_ENABLED) { ... } else { ... }' 的 else 分支，保留 if 分支内容",
            "删除 ReaderFeatureFlagsTest.kt 中所有 'SETTINGS_PANEL_V5_ENABLED' 相关断言"
        )
    },
    @{
        Id = 9
        Name = "删除 ReaderSessionState.kt (若满足双条件)"
        Kind = "conditional-file"
        Path = "app/src/main/java/com/shuli/reader/feature/reader/settings/ReaderSessionState.kt"
        Symbol = "ReaderSessionState"
        PreCheckDesc = "双条件：(1) 自身无外部引用；(2) sessionState/SessionState 已由 BookSessionManager/ReaderUiState 承载。"
        ExpectedRefsAfterBranchRemoval = -2  # -2 表示"双条件判断"
        Condition2Symbol = "sessionState|SessionState"
        Condition2Whitelist = @("BookSessionManager", "ReaderUiState")
    }
)

$results = @()

foreach ($cand in $candidates) {
    if ($cand.Id -lt $StartFrom) {
        Write-Host "[SKIP] #$($cand.Id) $($cand.Name) (before -StartFrom)" -ForegroundColor DarkGray
        continue
    }

    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "#$($cand.Id) $($cand.Name)" -ForegroundColor Cyan
    Write-Host "================================================================" -ForegroundColor Cyan

    # 本项起点 tag（用于单项回滚）
    $itemTag = "refactor-p05-item$($cand.Id)-$timestamp"
    if (-not $DryRun) {
        git tag $itemTag
        Write-Host "[TAG] $itemTag" -ForegroundColor DarkGray
    }

    # ── 预检查 ──
    Write-Host "[PRE-CHECK] $($cand.PreCheckDesc)" -ForegroundColor Yellow
    $refCount = Count-References $cand.Symbol
    Write-Host "  current '$($cand.Symbol)' references: $refCount" -ForegroundColor Cyan

    $proceed = $true

    if ($cand.ExpectedRefsAfterBranchRemoval -eq -2) {
        # 双条件判断 (ReaderSessionState)
        # 条件1: 自身无外部引用（只命中自身文件）
        $cond1 = ($refCount -le 1)
        # 条件2: sessionState/SessionState 已由白名单文件承载
        $cond2Hits = rg -l $cand.Condition2Symbol "app/src/main/java/com/shuli/reader/feature/reader/" 2>$null
        $cond2 = ($cond2Hits | Where-Object {
            $name = [System.IO.Path]::GetFileNameWithoutExtension($_)
            $cand.Condition2Whitelist -contains $name
        }).Count -gt 0

        if (-not $cond1) {
            Write-Host "  [BLOCKED] Condition 1 failed: still has external references" -ForegroundColor Red
            $proceed = $false
        }
        if (-not $cond2) {
            Write-Host "  [BLOCKED] Condition 2 failed: no semantic replacement found in whitelist" -ForegroundColor Red
            $proceed = $false
        }
        if ($cond1 -and $cond2) {
            Write-Host "  [OK] Both conditions met - safe to delete" -ForegroundColor Green
        }
    } elseif ($cand.ExpectedRefsAfterBranchRemoval -eq -1) {
        # 人工判断
        Write-Host "  [MANUAL] Review references manually:" -ForegroundColor Yellow
        rg -n $cand.Symbol "app/src/main/java" "app/src/test" "app/src/androidTest" 2>$null |
            ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        if (-not (Prompt-Continue "Proceed with deletion?")) { $proceed = $false }
    } elseif ($refCount -gt 5) {
        Write-Host "  [WARN] High reference count ($refCount). Likely has active callers beyond the old branch." -ForegroundColor Yellow
        Write-Host "  [ACTION] Run the following to inspect:" -ForegroundColor Yellow
        Write-Host "    rg -n '$($cand.Symbol)' app/src/main/java app/src/test app/src/androidTest" -ForegroundColor DarkGray
        if (-not (Prompt-Continue "Proceed anyway?")) { $proceed = $false }
    }

    if (-not $proceed) {
        Write-Host "[ABORT] #$($cand.Id) skipped" -ForegroundColor Red
        $results += [PSCustomObject]@{ Id = $cand.Id; Name = $cand.Name; Status = "SKIPPED"; Tag = $itemTag }
        continue
    }

    if ($DryRun) {
        Write-Host "[DRY-RUN] would delete: $($cand.Path)" -ForegroundColor Yellow
        if ($cand.ExtraActions) {
            foreach ($act in $cand.ExtraActions) {
                Write-Host "[DRY-RUN] would: $act" -ForegroundColor Yellow
            }
        }
        $results += [PSCustomObject]@{ Id = $cand.Id; Name = $cand.Name; Status = "DRY-RUN"; Tag = $itemTag }
        continue
    }

    # ── 执行删除 ──
    Write-Host "[EXECUTE] deleting..." -ForegroundColor Cyan

    $ok = $true
    if ($cand.Kind -eq "file" -or $cand.Kind -eq "conditional-file") {
        $ok = Git-SafeDelete $cand.Path
    } elseif ($cand.Kind -eq "field") {
        # 字段删除：需要调用方先做 ExtraActions，然后人工编辑文件
        if ($cand.ExtraActions) {
            Write-Host "  [MANUAL] Required pre-actions:" -ForegroundColor Yellow
            foreach ($act in $cand.ExtraActions) {
                Write-Host "    * $act" -ForegroundColor Yellow
            }
            if (-not (Prompt-Continue "Pre-actions completed. Mark field as deleted in $($cand.Path) now?")) {
                $ok = $false
            } else {
                # 提示人工删除字段本身
                Write-Host "  [MANUAL] Now remove the '$($cand.Symbol)' field declaration from:" -ForegroundColor Yellow
                Write-Host "    $($cand.Path)" -ForegroundColor Yellow
                if (-not (Prompt-Continue "Field declaration removed?")) { $ok = $false }
            }
        }
    }

    if (-not $ok) {
        Write-Host "[ROLLBACK] git reset --hard $itemTag" -ForegroundColor Red
        git reset --hard $itemTag
        $results += [PSCustomObject]@{ Id = $cand.Id; Name = $cand.Name; Status = "ROLLED-BACK"; Tag = $itemTag }
        continue
    }

    # ── 编译验证 ──
    if (-not (Compile-Debug)) {
        Write-Host "[ROLLBACK] compile failed, reverting to $itemTag" -ForegroundColor Red
        $answer = Read-Host "  Rollback now? [Y/n]"
        if ($answer -ne 'n' -and $answer -ne 'N') {
            git reset --hard $itemTag
            $results += [PSCustomObject]@{ Id = $cand.Id; Name = $cand.Name; Status = "ROLLED-BACK"; Tag = $itemTag }
            continue
        } else {
            Write-Host "  [CONTINUE] user chose to fix manually" -ForegroundColor Yellow
        }
    }

    # ── 后检查 ──
    if ($cand.ExpectedRefsAfterBranchRemoval -ge 0) {
        $postRefs = Count-References $cand.Symbol
        if ($postRefs -gt $cand.ExpectedRefsAfterBranchRemoval) {
            Write-Host "  [WARN] post-delete refs = $postRefs (expected $($cand.ExpectedRefsAfterBranchRemoval))" -ForegroundColor Yellow
            Write-Host "  [ACTION] remaining references:" -ForegroundColor Yellow
            rg -n $cand.Symbol "app/src/main/java" "app/src/test" "app/src/androidTest" 2>$null |
                ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        } else {
            Write-Host "  [OK] post-delete refs = $postRefs" -ForegroundColor Green
        }
    }

    $results += [PSCustomObject]@{ Id = $cand.Id; Name = $cand.Name; Status = "OK"; Tag = $itemTag }
}

# ── 总结 ──
Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "P0.5 Summary" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
$results | Format-Table -AutoSize | Out-String | Write-Host

$failed = $results | Where-Object { $_.Status -eq "ROLLED-BACK" -or $_.Status -eq "SKIPPED" }
if ($failed) {
    Write-Host "[RESULT] P0.5 incomplete. $($failed.Count) item(s) need attention." -ForegroundColor Red
    Write-Host "[NEXT]   Fix the failed items, then re-run with -StartFrom <first-failed-id>" -ForegroundColor Yellow
    Pop-Location
    exit 1
} else {
    Write-Host "[RESULT] P0.5 complete. Ready for P1." -ForegroundColor Green
    Write-Host "[NEXT]   git tag refactor-p05-done-$(Get-Date -Format 'yyyyMMdd')" -ForegroundColor Cyan
    if (-not $DryRun) {
        git tag "refactor-p05-done-$(Get-Date -Format 'yyyyMMdd')"
    }
    Pop-Location
    exit 0
}
