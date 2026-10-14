#!/usr/bin/env bash
# Exercise production helpers/wrappers; only Docker/GC command boundaries are fake.
set -euo pipefail
SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT
ROOT="$temporary/repo"
mkdir -p "$ROOT/scripts/lib" "$temporary/bin" "$temporary/state" "$temporary/cache" "$temporary/saved"
cp "$SOURCE_ROOT"/{Dockerfile,.dockerignore,flake.nix,flake.lock,build.sbt} "$ROOT/"
cp "$SOURCE_ROOT/scripts/lib/docker-image.sh" "$ROOT/scripts/lib/"
cp "$SOURCE_ROOT/scripts/ci-docker-"{export,load}"-image.sh" "$ROOT/scripts/"
export FAKE_STATE="$temporary/state" SMITHYSTACHE_DOCKER_EXPORT_DIR="$temporary/cache"
export PATH="$temporary/bin:$PATH"
export SMITHYSTACHE_TEST_IMAGE=smithystache-test:local SMITHYSTACHE_DOCKER_PLATFORM=linux/amd64
FAKE_ID="sha256:$(printf '%064d' 1)"
export FAKE_ID
cat > "$temporary/bin/docker" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_STATE/commands"
case "$1" in
  image)
    ref="${!#}"
    if [[ "$ref" == "$FAKE_ID" ]]; then
      [[ -f "$FAKE_STATE/loaded" ]] || exit 1
      id="$(cat "$FAKE_STATE/loaded")"
    else
      [[ -f "$FAKE_STATE/tag" ]] || exit 1
      id="$(cat "$FAKE_STATE/tag")"
    fi
    if [[ "${3:-}" == --format ]]; then
      case "$4" in
        '{{.Id}}') printf '%s\n' "$id" ;;
        *) cat "$FAKE_STATE/platform" ;;
      esac
    fi
    ;;
  build)
    printf '%s\n' "$FAKE_ID" > "$FAKE_STATE/tag"
    printf '%s\n' "$SMITHYSTACHE_DOCKER_PLATFORM" > "$FAKE_STATE/platform"
    ;;
  save)
    [[ "$2" == "$FAKE_ID" ]]
    printf '%s\n' "$2" "$(cat "$FAKE_STATE/platform")"
    ;;
  load)
    mapfile -t payload
    [[ ${#payload[@]} -eq 2 ]] || exit 1
    printf '%s\n' "${payload[0]}" > "$FAKE_STATE/loaded"
    printf '%s\n' "${payload[1]}" > "$FAKE_STATE/platform"
    if [[ ! -f "$FAKE_STATE/wrong-load-output" ]]; then
      printf 'Loaded image ID: %s\n' "${payload[0]}"
    else
      echo 'Loaded image: unrelated:local'
    fi
    ;;
  tag) cp "$FAKE_STATE/loaded" "$FAKE_STATE/tag" ;;
  builder) [[ "$2" == prune ]] ;;
  *) echo "unexpected fake Docker command: $*" >&2; exit 1 ;;
esac
FAKE
printf '#!/usr/bin/env bash\nexit 0\n' > "$temporary/bin/nix-collect-garbage"
chmod +x "$temporary/bin/"*
cd "$ROOT"
# shellcheck source=scripts/lib/docker-image.sh
source "$ROOT/scripts/lib/docker-image.sh"
archive="$SMITHYSTACHE_DOCKER_EXPORT_DIR/smithystache-test-image.tar.gz"
metadata="$SMITHYSTACHE_DOCKER_EXPORT_DIR/smithystache-test-image.metadata"
marker_path="$SMITHYSTACHE_DOCKER_INPUT_HASH_FILE"

smithystache_ensure_docker_image
cp "$marker_path" "$temporary/producer-marker"
bash scripts/ci-docker-export-image.sh
cp "$archive" "$metadata" "$temporary/saved/"
printf '%s\n' 'PASS producer build/export'

fresh_consumer() {
  rm -f "$marker_path" "$archive" "$metadata" "$FAKE_STATE/"*
  : > "$FAKE_STATE/commands"
  cp "$temporary/saved/"* "$SMITHYSTACHE_DOCKER_EXPORT_DIR/"
}
assert_rebuild() {
  local name="$1"
  # Even a stale marker left from earlier work must be removed on rejection.
  cp "$temporary/producer-marker" "$marker_path"
  bash scripts/ci-docker-load-image.sh
  [[ ! -e "$marker_path" ]]
  smithystache_ensure_docker_image
  grep -q '^build ' "$FAKE_STATE/commands"
  printf 'PASS %s => rebuild\n' "$name"
}
fresh_consumer
bash scripts/ci-docker-load-image.sh
cmp "$temporary/producer-marker" "$marker_path"
smithystache_ensure_docker_image
if grep -q '^build ' "$FAKE_STATE/commands"; then exit 1; fi
before="$(sha256sum "$archive" "$metadata")"
bash scripts/ci-docker-export-image.sh
[[ "$before" == "$(sha256sum "$archive" "$metadata")" ]]
if grep -q '^save ' "$FAKE_STATE/commands"; then exit 1; fi
echo 'PASS fresh-job round trip => reuse without build/export'

for scenario in missing malformed extra hash platform id checksum nul; do
  fresh_consumer
  case "$scenario" in
    missing) rm "$metadata" ;;
    malformed) printf 'not metadata\n' > "$metadata" ;;
    extra) echo extra >> "$metadata" ;;
    hash) sed -i "2c$(printf '%064d' 2)" "$metadata" ;;
    platform) sed -i '4clinux/arm64' "$metadata" ;;
    id) sed -i "3csha256:$(printf '%064d' 2)" "$metadata" ;;
    checksum) sed -i "5c$(printf '%064d' 2)" "$metadata" ;;
    nul) printf '\0' >> "$metadata" ;;
  esac
  assert_rebuild "$scenario metadata"
done
for scenario in absent corrupt invalid-gzip symlink-archive symlink-metadata wrong-load-output loaded-platform; do
  fresh_consumer
  case "$scenario" in
    absent) rm "$archive" ;;
    corrupt) echo corrupt >> "$archive" ;;
    invalid-gzip)
      echo invalid > "$archive"
      sed -i "5c$(sha256sum "$archive" | awk '{print $1}')" "$metadata"
      ;;
    symlink-archive) rm "$archive"; ln -s "$temporary/saved/$(basename "$archive")" "$archive" ;;
    symlink-metadata) rm "$metadata"; ln -s "$temporary/saved/$(basename "$metadata")" "$metadata" ;;
    wrong-load-output) touch "$FAKE_STATE/wrong-load-output" ;;
    loaded-platform)
      printf '%s\n' "$FAKE_ID" linux/arm64 | gzip > "$archive"
      sed -i "5c$(sha256sum "$archive" | awk '{print $1}')" "$metadata"
      ;;
  esac
  assert_rebuild "$scenario"
done

fresh_consumer
echo '# changed input' >> build.sbt
assert_rebuild 'changed producer input'
cp "$SOURCE_ROOT/build.sbt" build.sbt
for input in Dockerfile .dockerignore flake.nix flake.lock \
  scripts/lib/docker-image.sh scripts/ci-docker-export-image.sh scripts/ci-docker-load-image.sh; do
  fresh_consumer
  echo '# changed producer input' >> "$input"
  assert_rebuild "changed $input"
  cp "$SOURCE_ROOT/$input" "$input"
done
fresh_consumer
SMITHYSTACHE_DOCKER_PLATFORM=linux/arm64 assert_rebuild 'changed requested platform'
fresh_consumer
sed -i "2c\$(touch $temporary/EXECUTED)" "$metadata"
assert_rebuild 'shell metacharacters'
[[ ! -e "$temporary/EXECUTED" ]]

fresh_consumer
printf '%s\n' "$FAKE_ID" > "$FAKE_STATE/tag"
printf '%s\n' linux/amd64 > "$FAKE_STATE/platform"
touch "$FAKE_STATE/wrong-load-output"
assert_rebuild 'colliding preexisting tag cannot replace load identity check'

fresh_consumer
mv "$SMITHYSTACHE_DOCKER_EXPORT_DIR" "$temporary/real-cache"
ln -s "$temporary/real-cache" "$SMITHYSTACHE_DOCKER_EXPORT_DIR"
bash scripts/ci-docker-load-image.sh
[[ ! -e "$marker_path" ]]
if grep -q '^load' "$FAKE_STATE/commands"; then exit 1; fi
rm "$SMITHYSTACHE_DOCKER_EXPORT_DIR"
mv "$temporary/real-cache" "$SMITHYSTACHE_DOCKER_EXPORT_DIR"
echo 'PASS cache directory symlink is not loaded'

fresh_consumer
bash scripts/ci-docker-load-image.sh
printf 'sha256:%064d\n' 2 > "$FAKE_STATE/tag"
bash scripts/ci-docker-export-image.sh
if grep -q '^save ' "$FAKE_STATE/commands"; then exit 1; fi
smithystache_ensure_docker_image
grep -q '^build ' "$FAKE_STATE/commands"
echo 'PASS replaced tag cannot be exported/reused with old producer marker'

fresh_consumer
printf 'DO NOT OVERWRITE\n' > "$temporary/victim"
ln -s "$temporary/victim" "$marker_path"
bash scripts/ci-docker-load-image.sh
[[ "$(cat "$temporary/victim")" == 'DO NOT OVERWRITE' ]]
if smithystache_ensure_docker_image; then exit 1; fi
rm "$marker_path"
echo 'PASS marker symlink fails closed without overwriting target'

fresh_consumer
mv "$ROOT/target" "$temporary/real-target"
ln -s "$temporary/real-target" "$ROOT/target"
bash scripts/ci-docker-load-image.sh
if smithystache_ensure_docker_image; then exit 1; fi
[[ ! -e "$marker_path" ]]
rm "$ROOT/target"
mv "$temporary/real-target" "$ROOT/target"
echo 'PASS target directory symlink is not written'
echo 'All Docker image cache regression cases passed.'
