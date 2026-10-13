$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (Test-Path (Join-Path $PSScriptRoot '.env')) {
    foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot '.env')) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}
$pythonPath = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
if (!(Test-Path -LiteralPath $pythonPath)) { throw 'Install backend requirements in backend/.venv first.' }
& $pythonPath -m uvicorn backend.main:app --host 127.0.0.1 --port 8787
