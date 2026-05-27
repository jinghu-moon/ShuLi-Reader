# setup-signing.ps1
# 交互式配置 Android Release 签名

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$keystoreDir = Join-Path $projectRoot "keystore"
$keystoreProperties = Join-Path $projectRoot "keystore.properties"
$buildGradle = Join-Path $projectRoot "app\build.gradle.kts"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   Android Release 签名配置工具" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "此脚本会帮你完成以下工作：" -ForegroundColor White
Write-Host "  1. 生成签名密钥库（.jks 文件）" -ForegroundColor Gray
Write-Host "  2. 创建 keystore.properties 存储密码" -ForegroundColor Gray
Write-Host "  3. 自动修改 build.gradle.kts 接入签名" -ForegroundColor Gray
Write-Host "  4. 更新 .gitignore 防止密钥泄露" -ForegroundColor Gray

# ── 1. 密码 ──

Write-Host ""
Write-Host "── 第 1 步：设置密码 ──" -ForegroundColor Yellow
Write-Host "  密码用于保护密钥库文件，至少 6 位。" -ForegroundColor Gray
Write-Host "  以后每次构建 release 包都需要此密码。" -ForegroundColor Gray

$storePassword = Read-Host -Prompt "`n  请输入密码" -AsSecureString
$storePasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePassword)
)

if ($storePasswordPlain.Length -lt 6) {
    Write-Host "`n  密码太短，至少需要 6 位" -ForegroundColor Red
    exit 1
}

$confirmPassword = Read-Host -Prompt "  请再次输入密码确认" -AsSecureString
$confirmPasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirmPassword)
)

if ($storePasswordPlain -ne $confirmPasswordPlain) {
    Write-Host "`n  两次密码不一致" -ForegroundColor Red
    exit 1
}

# ── 2. 别名 ──

Write-Host ""
Write-Host "── 第 2 步：设置密钥别名 ──" -ForegroundColor Yellow
Write-Host "  别名是密钥库中这个密钥的标识名，相当于给密钥起个名字。" -ForegroundColor Gray
Write-Host "  直接回车使用默认值 [shuli]。" -ForegroundColor Gray

$keyAlias = Read-Host -Prompt "`n  密钥别名"
if ([string]::IsNullOrWhiteSpace($keyAlias)) { $keyAlias = "shuli" }

# ── 3. 有效期 ──

Write-Host ""
Write-Host "── 第 3 步：设置有效期 ──" -ForegroundColor Yellow
Write-Host "  密钥的有效天数，过期后无法再用此密钥签名。" -ForegroundColor Gray
Write-Host "  Google Play 要求至少到 2033 年。建议设大一些。" -ForegroundColor Gray
Write-Host "  直接回车使用默认值 [10000]（约 27 年）。" -ForegroundColor Gray

$validity = Read-Host -Prompt "`n  有效期（天）"
if ([string]::IsNullOrWhiteSpace($validity)) { $validity = 10000 }

# ── 4. 证书信息 ──

Write-Host ""
Write-Host "── 第 4 步：填写证书信息 ──" -ForegroundColor Yellow
Write-Host "  这些信息会写入签名证书，标识应用的发布者身份。" -ForegroundColor Gray
Write-Host "  个人应用可以随便填，不影响功能。" -ForegroundColor Gray

Write-Host ""
Write-Host "  --- 应用/发布者信息 ---" -ForegroundColor DarkGray

$cn = Read-Host -Prompt "  应用/开发者名称 (CN) [ShuLi Reader]"
if ([string]::IsNullOrWhiteSpace($cn)) { $cn = "ShuLi Reader" }

$ou = Read-Host -Prompt "  部门/团队 (OU) [Development]"
if ([string]::IsNullOrWhiteSpace($ou)) { $ou = "Development" }

$org = Read-Host -Prompt "  组织/公司 (O) [Personal]"
if ([string]::IsNullOrWhiteSpace($org)) { $org = "Personal" }

Write-Host ""
Write-Host "  --- 地理位置（可选，直接回车跳过） ---" -ForegroundColor DarkGray

$loc = Read-Host -Prompt "  城市 (L)"
$state = Read-Host -Prompt "  省份 (ST)"

$country = Read-Host -Prompt "  国家代码 (C) [CN]"
if ([string]::IsNullOrWhiteSpace($country)) { $country = "CN" }

# ── 5. 生成 Keystore ──

Write-Host ""
Write-Host "── 第 5 步：生成密钥库 ──" -ForegroundColor Yellow

if (-not (Test-Path $keystoreDir)) {
    New-Item -ItemType Directory -Path $keystoreDir | Out-Null
}

$keystorePath = Join-Path $keystoreDir "release.jks"

if (Test-Path $keystorePath) {
    Write-Host ""
    Write-Host "  密钥库已存在: $keystorePath" -ForegroundColor Yellow
    $overwrite = Read-Host -Prompt "  是否覆盖？(y/N)"
    if ($overwrite -ne "y" -and $overwrite -ne "Y") {
        Write-Host "  已取消" -ForegroundColor Red
        exit 1
    }
    Remove-Item $keystorePath
}

# 构建 DN
$dnParts = @("CN=$cn", "OU=$ou", "O=$org")
if ($loc) { $dnParts += "L=$loc" }
if ($state) { $dnParts += "ST=$state" }
$dnParts += "C=$country"
$dn = ($dnParts -join ", ")

# 查找 keytool
$keytool = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $candidate) { $keytool = $candidate }
}
if (-not $keytool) {
    # 尝试 Android Studio 自带 JDK
    $asJdk = "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
    if (Test-Path $asJdk) { $keytool = $asJdk }
}
if (-not $keytool) {
    # 从 PATH 中查找
    $found = Get-Command keytool -ErrorAction SilentlyContinue
    if ($found) { $keytool = $found.Source }
}
if (-not $keytool) {
    Write-Host "`n  找不到 keytool。请设置 JAVA_HOME 环境变量。" -ForegroundColor Red
    Write-Host "  Android Studio 自带的 JDK 通常在:" -ForegroundColor Gray
    Write-Host "    $env:LOCALAPPDATA\Programs\Android Studio\jbr" -ForegroundColor Gray
    exit 1
}

Write-Host "`n  正在生成密钥库..." -ForegroundColor Gray

& $keytool -genkeypair `
    -v `
    -keystore $keystorePath `
    -keyalg RSA `
    -keysize 2048 `
    -validity $validity `
    -alias $keyAlias `
    -storepass $storePasswordPlain `
    -keypass $storePasswordPlain `
    -dname $dn

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n  密钥库生成失败" -ForegroundColor Red
    exit 1
}

Write-Host "`n  密钥库已生成: $keystorePath" -ForegroundColor Green

# ── 6. 写入 keystore.properties ──

Write-Host "`n  正在写入 keystore.properties..." -ForegroundColor Gray

$relPath = "keystore/release.jks"

$content = @"
# 由 setup-signing.ps1 自动生成
# 此文件包含敏感信息，请勿提交到版本控制
storePassword=$storePasswordPlain
keyPassword=$storePasswordPlain
keyAlias=$keyAlias
storeFile=$relPath
"@

Set-Content -Path $keystoreProperties -Value $content -Encoding UTF8
Write-Host "  配置已写入: $keystoreProperties" -ForegroundColor Green

# ── 7. 更新 build.gradle.kts ──

Write-Host "`n  正在更新 build.gradle.kts..." -ForegroundColor Gray

$gradleContent = Get-Content $buildGradle -Raw

# 检查是否已有 signingConfigs
if ($gradleContent -match "signingConfigs") {
    Write-Host "  signingConfigs 已存在，跳过" -ForegroundColor Yellow
} else {
    # 在 android { 之后插入 signingConfigs
    $signingBlock = @'
    signingConfigs {
        create("release") {
            val props = Properties()
            val f = rootProject.file("keystore.properties")
            if (f.exists()) {
                props.load(f.inputStream())
            }
            storeFile = rootProject.file(props.getProperty("storeFile", ""))
            storePassword = props.getProperty("storePassword", "")
            keyAlias = props.getProperty("keyAlias", "")
            keyPassword = props.getProperty("keyPassword", "")
        }
    }

'@

    # 在 "android {" 后插入 signingConfigs（同时添加 import）
    $gradleContent = $gradleContent -replace "(plugins \{)", "import java.util.Properties`n`n`$1"
    $gradleContent = $gradleContent -replace "(android \{)", "`$1`n$signingBlock"

    # 在 release buildType 的 isShrinkResources 后添加 signingConfig
    $gradleContent = $gradleContent -replace `
        "(isShrinkResources = true)", `
        "`$1`n            signingConfig = signingConfigs.getByName(`"release`")"

    Set-Content -Path $buildGradle -Value $gradleContent -Encoding UTF8
    Write-Host "  build.gradle.kts 已更新" -ForegroundColor Green
}

# ── 8. 更新 .gitignore ──

Write-Host "`n  正在更新 .gitignore..." -ForegroundColor Gray

$gitignore = Join-Path $projectRoot ".gitignore"
$gitignoreContent = if (Test-Path $gitignore) { Get-Content $gitignore -Raw } else { "" }

$entries = @("keystore/", "keystore.properties", "*.jks")
$added = $false

foreach ($entry in $entries) {
    if ($gitignoreContent -notmatch [regex]::Escape($entry)) {
        Add-Content -Path $gitignore -Value $entry
        $added = $true
    }
}

if ($added) {
    Write-Host "  .gitignore 已更新（排除密钥文件）" -ForegroundColor Green
} else {
    Write-Host "  .gitignore 已包含所需规则" -ForegroundColor Gray
}

# ── 完成 ──

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "   配置完成！" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  生成的文件：" -ForegroundColor White
Write-Host "    keystore/release.jks    - 签名密钥库" -ForegroundColor Gray
Write-Host "    keystore.properties     - 密码配置" -ForegroundColor Gray
Write-Host ""
Write-Host "  构建 release 包：" -ForegroundColor White
Write-Host "    ./gradlew assembleRelease" -ForegroundColor Yellow
Write-Host ""
Write-Host "  APK 输出位置：" -ForegroundColor White
Write-Host "    app/build/outputs/apk/release/" -ForegroundColor Yellow
Write-Host ""
