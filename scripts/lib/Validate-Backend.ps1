function Get-SmithplatesValidateUsage {
  @'
usage: .\validate.ps1 [lint,test|build|test|...] [-Target TARGET]

Actions (comma-separated):
  build, lint   linters and compile (Scala + template harnesses)
  test          test suites (Scala + template harnesses)

Default: lint,test

Optional -Target scopes lint/test to plugin, python, python/db, or a dialect path.
Uses Nix when available (preferred), otherwise Docker. Override with $env:SMITHYSTACHE_VALIDATE_BACKEND = "nix" or "docker".
When multiple actions are requested, Nix/Docker is entered once for the full run.
'@
  Write-Host (Get-SmithplatesValidateTargetUsage)
}

function Get-SmithplatesDockerImageInputHash {
  param([Parameter(Mandatory = $true)][string]$Root)

  $hashes =
    @('Dockerfile', 'flake.nix', 'flake.lock') |
    ForEach-Object {
      $path = Join-Path $Root $_
      if (-not (Test-Path -LiteralPath $path)) {
        throw "missing file required for Docker image hash: $path"
      }
      (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }

  $bytes = [System.Text.Encoding]::UTF8.GetBytes(($hashes -join "`n") + "`n")
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    return ([BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant())
  } finally {
    $sha.Dispose()
  }
}

function Test-SmithplatesDockerImageExists {
  param([Parameter(Mandatory = $true)][string]$Image)

  & docker image inspect $Image 2>$null | Out-Null
  return ($LASTEXITCODE -eq 0)
}

function Ensure-SmithplatesDockerImage {
  param([Parameter(Mandatory = $true)][string]$Root)

  $image = if ($env:SMITHYSTACHE_TEST_IMAGE) { $env:SMITHYSTACHE_TEST_IMAGE } else { 'smithystache-test:local' }
  $cacheDir = Join-Path $Root 'target/docker-test'
  $hashFile = Join-Path $cacheDir 'image-input.hash'
  $currentHash = Get-SmithplatesDockerImageInputHash -Root $Root
  $needsBuild = -not (Test-SmithplatesDockerImageExists -Image $image)

  if (-not $needsBuild -and (Test-Path -LiteralPath $hashFile)) {
    $storedHash = (Get-Content -LiteralPath $hashFile -Raw).Trim()
    if ($storedHash -ne $currentHash) {
      $needsBuild = $true
    }
  } elseif (-not $needsBuild) {
    $needsBuild = $true
  }

  if ($needsBuild) {
    New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
    Write-Host "Building Docker test image ($image)..."
    Write-Host 'The first build can take several minutes while Nix downloads dependencies; later builds reuse cached layers and are much faster.'
    & docker build -t $image -f (Join-Path $Root 'Dockerfile') $Root
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Set-Content -LiteralPath $hashFile -Value $currentHash -NoNewline
  } else {
    Write-Host "Reusing Docker test image ($image); rebuilds when Dockerfile or flake inputs change."
  }

  return $image
}

function Invoke-SmithplatesDockerRun {
  param(
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string[]]$Command,
    [switch]$MountDockerSocket
  )

  $image = Ensure-SmithplatesDockerImage -Root $Root
  $dockerArgs = @('run', '--rm')
  if ($env:SMITHYSTACHE_VALIDATE_TARGET) {
    $dockerArgs += @('-e', "SMITHYSTACHE_VALIDATE_TARGET=$($env:SMITHYSTACHE_VALIDATE_TARGET)")
  }
  if ($env:SMITHYSTACHE_PYTHON_SERVICE_TYPE) {
    $dockerArgs += @('-e', "SMITHYSTACHE_PYTHON_SERVICE_TYPE=$($env:SMITHYSTACHE_PYTHON_SERVICE_TYPE)")
  }
  if ($env:SMITHYSTACHE_PYTHON_IMPL) {
    $dockerArgs += @('-e', "SMITHYSTACHE_PYTHON_IMPL=$($env:SMITHYSTACHE_PYTHON_IMPL)")
  }
  if ($MountDockerSocket) {
    if ($IsWindows -or $env:OS -match 'Windows') {
      $dockerArgs += @('-v', '//var/run/docker.sock:/var/run/docker.sock')
    } else {
      $dockerArgs += @('-v', '/var/run/docker.sock:/var/run/docker.sock')
    }
  }
  $dockerArgs += @(
    '-v', "${Root}:/smithystache",
    '-w', '/smithystache',
    $image
  ) + $Command

  & docker @dockerArgs
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Test-SmithplatesInNixShell {
  return -not [string]::IsNullOrEmpty($env:IN_NIX_SHELL)
}

function Test-SmithplatesValidateActionsNeedDockerSocket {
  param(
    [Parameter(Mandatory = $true)][string[]]$Actions,
    [string]$Target = 'all'
  )

  if ($Actions -notcontains 'test') {
    return $false
  }
  return Test-SmithplatesValidateTargetNeedsPostgresDocker -Target $Target
}

function Get-SmithplatesValidateBackend {
  param([Parameter(Mandatory = $true)][string]$Root)

  if ($env:SMITHYSTACHE_VALIDATE_BACKEND) {
    switch ($env:SMITHYSTACHE_VALIDATE_BACKEND) {
      'nix' { return 'nix' }
      'docker' { return 'docker' }
      default {
        Write-Error 'SMITHYSTACHE_VALIDATE_BACKEND must be nix or docker'
        exit 2
      }
    }
  }

  if (Get-Command nix -ErrorAction SilentlyContinue) {
    if (Test-Path -LiteralPath (Join-Path $Root 'flake.nix')) {
      & nix flake metadata $Root 2>$null | Out-Null
      if ($LASTEXITCODE -eq 0) { return 'nix' }
    }
  }

  if (Get-Command docker -ErrorAction SilentlyContinue) {
    & docker info 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { return 'docker' }
  }

  return 'none'
}

function Invoke-SmithplatesValidateActionsWithBackend {
  param(
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$Backend,
    [Parameter(Mandatory = $true)][string[]]$Actions,
    [string]$Target = 'all'
  )

  $env:SMITHYSTACHE_VALIDATE_TARGET = $Target
  Set-SmithplatesPythonTargetEnv -Target $Target

  switch ($Backend) {
    'nix' {
      if (Test-SmithplatesInNixShell) {
        . (Join-Path $Root 'scripts/lib/Validate-Actions.ps1')
        Invoke-SmithplatesValidateActions -Actions $Actions
      } else {
        $validateActionArgs = @('./scripts/run-validate-actions.ps1') + $Actions
        & nix develop $Root --accept-flake-config --command pwsh -NoProfile -File @validateActionArgs
      }
    }
    'docker' {
      $command = @('./scripts/run-validate-actions.sh') + $Actions
      if (Test-SmithplatesValidateActionsNeedDockerSocket -Actions $Actions -Target $Target) {
        Invoke-SmithplatesDockerRun -Root $Root -MountDockerSocket -Command $command
      } else {
        Invoke-SmithplatesDockerRun -Root $Root -Command $command
      }
    }
    default {
      Write-Error @'
Need Nix (flakes) or a running Docker daemon to validate.
  Nix: https://nixos.org/download/
  Docker: https://docs.docker.com/get-docker/
'@
      exit 1
    }
  }
}

function Normalize-SmithplatesValidateAction {
  param([Parameter(Mandatory = $true)][string]$Action)

  switch ($Action.ToLowerInvariant()) {
    'build' { return 'lint' }
    'lint' { return 'lint' }
    'test' { return 'test' }
    default {
      Write-Error "unknown validate action: $Action"
      Write-Host (Get-SmithplatesValidateUsage)
      exit 2
    }
  }
}

function Invoke-SmithplatesValidate {
  param(
    [Parameter(Mandatory = $true)][string]$Root,
    [string]$ActionSpec = 'lint,test',
    [string]$Target = 'all'
  )

  $normalizedTarget = Normalize-SmithplatesValidateTarget -Target $Target
  $env:SMITHYSTACHE_VALIDATE_TARGET = $normalizedTarget
  Set-SmithplatesPythonTargetEnv -Target $normalizedTarget

  $backend = Get-SmithplatesValidateBackend -Root $Root
  Write-Host "==> validate ($ActionSpec, target=$normalizedTarget) via $backend"

  $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
  $actions = [System.Collections.Generic.List[string]]::new()

  foreach ($part in ($ActionSpec -split ',')) {
    $trimmed = $part.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
    $normalized = Normalize-SmithplatesValidateAction -Action $trimmed
    if ($seen.Add($normalized)) {
      [void]$actions.Add($normalized)
    }
  }

  if ($actions.Count -eq 0) {
    Write-Host (Get-SmithplatesValidateUsage)
    exit 2
  }

  Invoke-SmithplatesValidateActionsWithBackend -Root $Root -Backend $backend -Actions $actions.ToArray() -Target $normalizedTarget
}
