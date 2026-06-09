# Run Smithplates linters and/or tests using Nix (preferred) or Docker.
#
#   .\validate.ps1                              # lint + test (default)
#   .\validate.ps1 build                        # lint and compile only
#   .\validate.ps1 test                         # tests only
#   .\validate.ps1 lint,test                    # explicit default
#   .\validate.ps1 lint -Target python/db/sqlite
#   .\validate.ps1 test -Target plugin
param(
  [Parameter(Position = 0)]
  [string]$Action = 'lint,test',
  [string]$Target = 'all'
)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
Set-Location -LiteralPath $Root

. (Join-Path $Root 'scripts/lib/Validate-Target.ps1')
. (Join-Path $Root 'scripts/lib/Validate-Backend.ps1')

Invoke-SmithplatesValidate -Root $Root -ActionSpec $Action -Target $Target
