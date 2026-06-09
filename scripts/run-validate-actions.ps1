# Run one or more validate actions (lint, test) in the current environment.
# Used by validate.ps1 when entering nix develop or Docker once for all actions.
param(
  [Parameter(Mandatory = $true, Position = 0)]
  [string[]]$Action
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $Root

. (Join-Path $Root 'scripts/lib/Validate-Actions.ps1')

Invoke-SmithplatesValidateActions -Actions $Action
