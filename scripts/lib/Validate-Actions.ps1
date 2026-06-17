function Invoke-SmithplatesPublishM2Once {
  if ($env:SMITHYSTACHE_PUBLISH_M2_DONE -eq '1') {
    Write-Host '==> publishM2 skipped (already completed)'
    return
  }

  if (-not (Get-Command sbtn -ErrorAction SilentlyContinue)) {
    Write-Error 'sbtn not on PATH (required for publishM2)'
    exit 1
  }

  Write-Host '==> publishM2'
  & sbtn publishM2
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  $env:SMITHYSTACHE_PUBLISH_M2_DONE = '1'
}

function Invoke-SmithplatesValidateRunExampleBuild {
  param([string]$Target)

  switch ($Target) {
    { $_ -in @('all', 'examples') } { & ./scripts/run-example-build.sh all; break }
    'examples/python' { & ./scripts/run-example-build.sh python; break }
  }
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Invoke-SmithplatesValidateRunLintForTarget {
  $target = if ($env:SMITHYSTACHE_VALIDATE_TARGET) { $env:SMITHYSTACHE_VALIDATE_TARGET } else { 'all' }
  switch ($target) {
    'all' {
      & ./scripts/run-linters.sh all
      if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
      Invoke-SmithplatesValidateRunExampleBuild -Target $target
      & ./scripts/run-example-linters.sh all
      break
    }
    'plugin' { & ./scripts/run-linters.sh scala; break }
    { $_ -in @('python', 'python/db', 'python/db/sqlite', 'python/db/postgres') } {
      & ./scripts/run-linters.sh templates
      break
    }
    { $_ -in @('examples', 'examples/python') } {
      Invoke-SmithplatesValidateRunExampleBuild -Target $target
      if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
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
    'all' {
      & ./scripts/run-tests.sh all
      if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
      & ./scripts/run-example-tests.sh all
      break
    }
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

  Invoke-SmithplatesPublishM2Once

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
