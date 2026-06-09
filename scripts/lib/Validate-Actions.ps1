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
      'lint' {
        & ./scripts/run-linters.sh all
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
      }
      'test' {
        & ./scripts/run-tests.sh all
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
      }
      default {
        Write-Error "unknown validate action: $action"
        exit 2
      }
    }
  }
}
