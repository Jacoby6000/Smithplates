#!/usr/bin/env bash
# Regenerate Smithplates artifacts from smithy/ into src/generated/, tests/, and db/migrations/.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${repo_root}"
sbtn 'smithplatesPlugin/Test/runMain com.jacoby6000.smithplates.plugin.generators.ExamplePythonBuild example/python'
