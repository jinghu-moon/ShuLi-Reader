$utf8 = New-Object System.Text.UTF8Encoding $false

$errs1 = $null
$src1 = [System.IO.File]::ReadAllText(
    (Resolve-Path -LiteralPath "scripts\refactor-p0-audit.ps1").Path,
    $utf8
)
$null = [System.Management.Automation.PSParser]::Tokenize($src1, [ref]$errs1)
if ($errs1.Count -eq 0) {
    Write-Host "p0-audit.ps1: SYNTAX OK" -ForegroundColor Green
} else {
    Write-Host "p0-audit.ps1: ERRORS ($($errs1.Count))" -ForegroundColor Red
    $errs1 | ForEach-Object { Write-Host "  Line $($_.TokenStartLine): $($_.Message)" }
}

$errs2 = $null
$src2 = [System.IO.File]::ReadAllText(
    (Resolve-Path -LiteralPath "scripts\refactor-p05-delete.ps1").Path,
    $utf8
)
$null = [System.Management.Automation.PSParser]::Tokenize($src2, [ref]$errs2)
if ($errs2.Count -eq 0) {
    Write-Host "p05-delete.ps1: SYNTAX OK" -ForegroundColor Green
} else {
    Write-Host "p05-delete.ps1: ERRORS ($($errs2.Count))" -ForegroundColor Red
    $errs2 | ForEach-Object { Write-Host "  Line $($_.TokenStartLine): $($_.Message)" }
}
