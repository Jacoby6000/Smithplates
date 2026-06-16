function Get-SmithplatesValidateTargetUsage {
  @'
Targets (--target / -Target):
  all                 full repo (default)
  plugin              Scala plugin modules only
  python              all Python service types (discovered)
  python/db           db service type only
  python/db/sqlite    db sqlite dialect + shared db files
  python/db/postgres  db postgres dialect + shared db files
  examples            all example reference projects
  examples/python     Python petstore reference (ruff, mypy, pytest, HTTP tests)
'@
}

function Normalize-SmithplatesValidateTarget {
  param([string]$Target = 'all')

  $normalized = if ([string]::IsNullOrWhiteSpace($Target)) { 'all' } else { $Target.Trim().ToLowerInvariant() }
  switch ($normalized) {
    'all' { return 'all' }
    'plugin' { return 'plugin' }
    'python' { return 'python' }
    'python/db' { return 'python/db' }
    'python/db/sqlite' { return 'python/db/sqlite' }
    'python/db/postgres' { return 'python/db/postgres' }
    'examples' { return 'examples' }
    'examples/python' { return 'examples/python' }
    default {
      Write-Error "unknown validate target: $Target"
      Write-Host (Get-SmithplatesValidateTargetUsage)
      exit 2
    }
  }
}

function Test-SmithplatesValidateTargetIsExamples {
  param([Parameter(Mandatory = $true)][string]$Target)

  return $Target -in @('examples', 'examples/python')
}

function Test-SmithplatesValidateTargetIsPython {
  param([Parameter(Mandatory = $true)][string]$Target)

  return $Target -in @('python', 'python/db', 'python/db/sqlite', 'python/db/postgres')
}

function Test-SmithplatesValidateTargetNeedsPostgresDocker {
  param([Parameter(Mandatory = $true)][string]$Target)

  return $Target -in @('all', 'plugin', 'python', 'python/db', 'python/db/postgres', 'examples', 'examples/python')
}

function Get-SmithplatesValidateExampleProject {
  param([Parameter(Mandatory = $true)][string]$Target)

  switch ($Target) {
    'examples/python' { return 'python' }
    'examples' { return 'all' }
    default { return '' }
  }
}

function Get-SmithplatesValidateTargetServiceType {
  param([Parameter(Mandatory = $true)][string]$Target)

  switch ($Target) {
    'python/db' { return 'db' }
    'python/db/sqlite' { return 'db' }
    'python/db/postgres' { return 'db' }
    'python' { return 'all' }
    default { return '' }
  }
}

function Get-SmithplatesValidateTargetImpl {
  param([Parameter(Mandatory = $true)][string]$Target)

  switch ($Target) {
    'python/db/sqlite' { return 'sqlite' }
    'python/db/postgres' { return 'postgres' }
    'python' { return 'all' }
    'python/db' { return 'all' }
    default { return '' }
  }
}

function Get-SmithplatesPythonServiceTypes {
  param([Parameter(Mandatory = $true)][string]$Root)

  $testsRoot = Join-Path $Root 'templates/python/tests'
  if (-not (Test-Path -LiteralPath $testsRoot)) {
    return @()
  }

  $serviceTypes = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  Get-ChildItem -LiteralPath $testsRoot -Directory | ForEach-Object {
    $srcRoot = Join-Path $_.FullName 'expected/src'
    if (-not (Test-Path -LiteralPath $srcRoot)) { return }
    Get-ChildItem -LiteralPath $srcRoot -Directory | ForEach-Object {
      [void]$serviceTypes.Add($_.Name)
    }
  }

  return $serviceTypes.ToArray() | Sort-Object
}

function Set-SmithplatesPythonTargetEnv {
  param([Parameter(Mandatory = $true)][string]$Target)

  Remove-Item Env:SMITHYSTACHE_PYTHON_SERVICE_TYPE -ErrorAction SilentlyContinue
  Remove-Item Env:SMITHYSTACHE_PYTHON_IMPL -ErrorAction SilentlyContinue

  if (-not (Test-SmithplatesValidateTargetIsPython -Target $Target)) {
    return
  }

  $serviceTypeFilter = Get-SmithplatesValidateTargetServiceType -Target $Target
  $implFilter = Get-SmithplatesValidateTargetImpl -Target $Target

  if ($serviceTypeFilter -and $serviceTypeFilter -ne 'all') {
    $env:SMITHYSTACHE_PYTHON_SERVICE_TYPE = $serviceTypeFilter
  }
  if ($implFilter -and $implFilter -ne 'all') {
    $env:SMITHYSTACHE_PYTHON_IMPL = $implFilter
  }
}
