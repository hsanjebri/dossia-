# Load repo-root .env into the process, then start Spring Boot (profile: local).
# Usage (from repo root):  .\scripts\run-backend.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$envFile = Join-Path $Root ".env"
if (Test-Path $envFile) {
  Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $i = $line.IndexOf("=")
    if ($i -lt 1) { return }
    $key = $line.Substring(0, $i).Trim()
    $val = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
    [Environment]::SetEnvironmentVariable($key, $val, "Process")
  }
  Write-Host "Loaded .env ($((Get-Content $envFile | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' }).Count) vars)"
} else {
  Write-Warning ".env not found at $envFile — Gemini may run offline"
}

if (-not $env:GEMINI_API_KEY) {
  Write-Warning "GEMINI_API_KEY is empty"
} else {
  Write-Host "GEMINI_API_KEY: set ($($env:GEMINI_API_KEY.Length) chars)"
}

$env:SPRING_PROFILES_ACTIVE = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { "local" }
& "$Root\mvnw.cmd" spring-boot:run
