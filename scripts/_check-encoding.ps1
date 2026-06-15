$scripts = @(
    "scripts\refactor-p0-audit.ps1",
    "scripts\refactor-p05-delete.ps1",
    "scripts\_validate-syntax.ps1"
)

foreach ($script in $scripts) {
    $f = Resolve-Path -LiteralPath $script
    $bytes = [System.IO.File]::ReadAllBytes($f)
    $hex = ($bytes[0..2] | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
    Write-Host "$script first 3 bytes: $hex"

    if ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Host "  $script: UTF-8 with BOM"
    } else {
        Write-Host "  $script: UTF-8 no BOM"
    }
}
