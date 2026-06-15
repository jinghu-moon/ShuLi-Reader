# Convert both scripts to UTF-8 WITH BOM so PowerShell 5.1 runtime parses them correctly
$utf8Bom = New-Object System.Text.UTF8Encoding $true
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$files = @(
    "scripts\refactor-p0-audit.ps1",
    "scripts\refactor-p05-delete.ps1",
    "scripts\_validate-syntax.ps1"
)

foreach ($f in $files) {
    $path = (Resolve-Path -LiteralPath $f).Path
    $content = [System.IO.File]::ReadAllText($path, $utf8NoBom)
    [System.IO.File]::WriteAllText($path, $content, $utf8Bom)
    Write-Host "Converted to UTF-8 with BOM: $f"
}
