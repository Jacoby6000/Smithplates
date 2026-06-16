function Invoke-SmithplatesValidateRunLintForTarget {
  $target = if ($env:SMITHYSTACHE_VALIDATE_TARGET) { $env:SMITHYSTACHE_VALIDATE_TARGET } else { 'all' }
  switch ($target) {
    'all' { & ./scripts/run-linters.sh all; break }
    'plugin' { & ./scripts/run-linters.sh scala; break }
    { $_ -in @('python', 'python/db', 'python/db/sqlite', 'python/db/postgres') } {
      & ./scripts/run-linters.sh templates
      break
    }
    { $_ -in @('examples', 'examples/python') } {
      $project = Get-SmithplatesValidateExampleProject -Target $target
      & ./scripts/run-example-linters.sh $project
      break
    }
    default {
      Write-Error "unknown validate target for lint: $target"
      exit 2
    }
  }
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Invoke-SmithplatesValidateRunTestForTarget {
  $target = if ($env:SMITHYSTACHE_VALIDATE_TARGET) { $env:SMITHYSTACHE_VALIDATE_TARGET } else { 'all' }
  switch ($target) {
    'all' { & ./scripts/run-tests.sh all; break }
    'plugin' { & ./scripts/run-tests.sh plugin; break }
    { $_ -in @('python', 'python/db', 'python/db/sqlite', 'python/db/postgres') } {
      & ./scripts/run-tests.sh templates
      break
    }
    { $_ -in @('examples', 'examples/python') } {
      $project = Get-SmithplatesValidateExampleProject -Target $target
      & ./scripts/run-example-tests.sh $project
      break
    }
    default {
      Write-Error "unknown validate target for test: $target"
      exit 2
    }
  }
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Invoke-SmithplatesValidateActions {
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$Actions
  )

  if ($Actions.Count -eq 0) {
    Write-Error 'no validate actions to run'
    exit 2
  }

  foreach ($action in $Actions) {
    switch ($action) {
      'lint' { Invoke-SmithplatesValidateRunLintForTarget }
      'test' { Invoke-SmithplatesValidateRunTestForTarget }
      default {
        Write-Error "unknown validate action: $action"
        exit 2
      }
    }
  }
}
