# One-time setup for PowerShell: makes `mvn` available as a normal command.
# It detects the newest Apache Maven installed under C:\Program Files\Apache.
$apacheRoot = Join-Path ${env:ProgramFiles} 'Apache'
$mavenDir = Get-ChildItem -Path $apacheRoot -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1

if (-not $mavenDir) {
    Write-Host 'Apache Maven was not found under C:\Program Files\Apache.' -ForegroundColor Red
    Write-Host 'Install Apache Maven first, then run this script again.'
    exit 1
}

$mvnCmd = Join-Path $mavenDir.FullName 'bin\mvn.cmd'
if (-not (Test-Path $mvnCmd)) {
    Write-Host "Maven executable not found: $mvnCmd" -ForegroundColor Red
    exit 1
}

$profileDir = Split-Path -Parent $PROFILE
if (-not (Test-Path $profileDir)) { New-Item -ItemType Directory -Path $profileDir -Force | Out-Null }

$markerStart = '# YATRAAI_MAVEN_START'
$markerEnd   = '# YATRAAI_MAVEN_END'
$profileText = if (Test-Path $PROFILE) { Get-Content $PROFILE -Raw } else { '' }
$block = @"
$markerStart
function mvn { & '$mvnCmd' @args }
$markerEnd
"@

$escapedStart = [regex]::Escape($markerStart)
$escapedEnd = [regex]::Escape($markerEnd)
$pattern = '(?s)' + $escapedStart + '.*?' + $escapedEnd
if ($profileText -match $pattern) {
    $profileText = [regex]::Replace($profileText, $pattern, $block.Trim())
} else {
    if ($profileText.Length -gt 0 -and -not $profileText.EndsWith("`n")) { $profileText += "`r`n" }
    $profileText += $block
}
Set-Content -Path $PROFILE -Value $profileText -Encoding UTF8

# Make it available in this PowerShell window immediately too.
function global:mvn { & $mvnCmd @args }
Write-Host "Done. Maven command configured: $mvnCmd" -ForegroundColor Green
Write-Host 'You can now use: mvn -version' -ForegroundColor Green
Write-Host 'After this, future PowerShell/VS Code terminals can use `mvn` normally.' -ForegroundColor Green
