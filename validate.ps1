# Run SmithyStache linters and/or tests using Nix (preferred) or Docker.
#
#   .\validate.ps1              # lint + test (default)
#   .\validate.ps1 build        # lint and compile only
#   .\validate.ps1 test         # tests only
#   .\validate.ps1 lint,test    # explicit default
param(
  [Parameter(Position = 0)]
  [string]$Action = 'lint,test'
)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
Set-Location -LiteralPath $Root

. (Join-Path $Root 'scripts/lib/Validate-Backend.ps1')

Invoke-SmithyStacheValidate -Root $Root -ActionSpec $Action
