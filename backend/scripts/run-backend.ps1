Param(
  [string]$EnvFile = (Join-Path $PSScriptRoot "..\\.env"),
  [string[]]$MvnArgs = @("spring-boot:run")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Import-DotEnv([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) {
    Write-Host "Env file not found: $Path"
    return
  }
  Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0) { return }
    if ($line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $name = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
  }
}

Import-DotEnv -Path $EnvFile

Push-Location (Resolve-Path "$PSScriptRoot\..")
try {
  & mvn @MvnArgs
} finally {
  Pop-Location
}
