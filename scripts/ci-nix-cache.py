#!/usr/bin/env python3
"""Bounded CI tooling transport. Integrity checks are not producer authentication."""

import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import resource
import secrets
import shutil
import signal
import stat
import subprocess
import sys
import time

VERSION = "2.24.9"
SCHEMA = "smithplates-nix-nar-v1"
INPUTS = sorted(("flake.lock", "flake.nix", "build.sbt",
                 ".github/actions/setup-validate/action.yml",
                 "scripts/ci-nix-cache.py", "scripts/test-ci-nix-cache.py"))
FILES = {"closure.nar.gz", "paths.txt", "manifest.json"}
PATH = re.compile(r"/nix/store/[0-9abcdfghijklmnpqrsvwxyz]{32}-[A-Za-z0-9+._?=-]{1,211}\Z")
GIB = 1024 ** 3
MAX_PATHS, MAX_NAR, MAX_PATH, MAX_ARCHIVE = 4096, 6 * GIB, GIB, 2 * GIB
MAX_METADATA, TIMEOUT, RESERVE = 1024 ** 2, 600, 2 * GIB
ROOT = Path(__file__).resolve().parent.parent


def status(message):
    print(f"Nix tooling cache: {message}", flush=True)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def run(*args, **kwargs):
    return subprocess.check_output(args, text=True, timeout=TIMEOUT, **kwargs).strip()


def identity():
    digest = hashlib.sha256()
    for name in INPUTS:
        data = (ROOT / name).read_bytes()  # Missing inputs are errors, not empty hashes.
        digest.update(name.encode() + b"\0" + hashlib.sha256(data).digest())
    repo = os.environ["GITHUB_REPOSITORY"].lower()
    require(re.fullmatch(r"[a-z0-9_.-]+/[a-z0-9_.-]+", repo), "repository")
    platform = f'{os.environ["RUNNER_OS"]}-{os.environ["RUNNER_ARCH"]}'
    require(re.fullmatch(r"[A-Za-z0-9_-]+", platform), "platform")
    return f"{SCHEMA}-{repo.replace('/', '-')}-{platform}-nix-{VERSION}-{digest.hexdigest()}"


def directory():
    parent = Path(os.environ["RUNNER_TEMP"])
    require(parent.is_absolute() and parent.is_dir() and not parent.is_symlink(), "runner temp")
    return parent / SCHEMA


def owned():
    base = directory()
    require(not base.is_symlink() and base.is_dir() and base.stat().st_uid == os.getuid(), "ownership")
    marker = base / "owner"
    require(not marker.is_symlink() and marker.is_file(), "owner marker")
    require(marker.read_text() == os.environ["SMITHPLATES_NIX_CACHE_OWNER"], "owner token")
    artifacts = base / "artifacts"
    require(artifacts.is_dir() and not artifacts.is_symlink(), "artifact directory")
    return base, artifacts


def output(name, value):
    with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
        stream.write(f"{name}={value}\n")


def prepare():
    base = directory()
    base.mkdir(mode=0o700)  # Never reuse or delete a preexisting directory.
    (base / "artifacts").mkdir(mode=0o700)
    token = secrets.token_hex(32)
    (base / "owner").write_text(token)
    with open(os.environ["GITHUB_ENV"], "a") as stream:
        stream.write(f"SMITHPLATES_NIX_CACHE_OWNER={token}\n")
    key = identity()
    output("key", key)
    output("path", str(base / "artifacts"))
    compatible = run("nix", "--version") == f"nix (Nix) {VERSION}"
    output("enabled", str(compatible).lower())
    if not compatible:
        status("version-mismatch; transport disabled, normal shell remains mandatory")


def regular(path, limit):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_size <= limit, f"file type/size: {path.name}")
    return info.st_size


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def path_list(paths):
    require(isinstance(paths, list) and 0 < len(paths) <= MAX_PATHS, "path count")
    require(all(isinstance(p, str) and PATH.fullmatch(p) for p in paths), "store path")
    require(paths == sorted(set(paths)), "path order/duplicates")
    return paths


def sizes_valid(sizes, paths):
    require(isinstance(sizes, dict) and set(sizes) == set(paths), "size identities")
    require(all(type(n) is int and 0 < n <= MAX_PATH for n in sizes.values()), "per-path limit")
    require(sum(sizes.values()) <= MAX_NAR, "closure limit")


def read_manifest(artifacts):
    require({p.name for p in artifacts.iterdir()} == FILES, "exact artifact filenames")
    for name in FILES:
        regular(artifacts / name, MAX_ARCHIVE if name == "closure.nar.gz" else MAX_METADATA)
    manifest = json.loads((artifacts / "manifest.json").read_text())
    require(manifest["schema"] == SCHEMA and manifest["key"] == identity(), "schema/key")
    require(manifest["nix"] == VERSION, "Nix version")
    require(manifest["os"] == os.environ["RUNNER_OS"] and
            manifest["arch"] == os.environ["RUNNER_ARCH"], "platform")
    producer = manifest["producer"]
    require(producer["repository"].lower() == os.environ["GITHUB_REPOSITORY"].lower() and
            producer["event"] == "push" and producer["ref"] == "refs/heads/main" and
            re.fullmatch(r"[0-9a-f]{40}", producer["sha"]), "producer metadata")
    paths = path_list((artifacts / "paths.txt").read_text().splitlines())
    require(manifest["paths"] == len(paths), "path count metadata")
    roots = path_list(manifest["roots"])
    require(len(roots) <= 2 and set(roots) <= set(paths), "roots")
    require(any(p.endswith("-env") for p in roots) and
            any(re.search(r"-bash-interactive-[^/]+$", p) for p in roots), "root identities")
    sizes_valid(manifest["sizes"], paths)
    archive = artifacts / "closure.nar.gz"
    require(archive.stat().st_size == manifest["archive_bytes"], "archive byte count")
    require(sha256(archive) == manifest["sha256"], "archive checksum")
    # Check gzip framing and decoded budget before the native Nix parser sees it.
    decoded = 0
    with gzip.open(archive, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            decoded += len(block)
            require(decoded <= MAX_NAR + MAX_METADATA * 4, "decoded limit")
    require(decoded == manifest["decoded_bytes"], "decoded byte count")
    return manifest, paths


def process_limits():
    # The legacy importer buffers individual NARs; never let it consume runner RAM.
    resource.setrlimit(resource.RLIMIT_AS, (3 * GIB, 3 * GIB))
    resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_ARCHIVE, MAX_ARCHIVE))


def import_limits():
    process_limits()
    resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_METADATA, MAX_METADATA))


def import_archive(archive):
    # Normal daemon client. No signature bypass or trust/config override.
    base, _ = owned()
    with (base / "imported.txt").open("w+b") as imported, subprocess.Popen(
                          ["nix-store", "--import"], stdin=subprocess.PIPE,
                          stdout=imported, stderr=None,
                          preexec_fn=import_limits) as process:
        try:
            with gzip.open(archive, "rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    process.stdin.write(block)
            process.stdin.close()
            require(process.wait(timeout=TIMEOUT) == 0, "native import failed")
            imported.seek(0)
            result = imported.read(MAX_METADATA + 1)
            require(len(result) <= MAX_METADATA, "import output limit")
            return result.decode().splitlines()
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()


def path_sizes(paths):
    data = json.loads(run("nix", "path-info", "--json", *paths))
    # Nix 2.24 uses a list; later versions use a path-keyed object.
    if isinstance(data, list):
        return {p["path"]: p["narSize"] for p in data}
    return {path: info["narSize"] for path, info in data.items()}


def restore():
    _, artifacts = owned()
    matched = os.environ.get("CACHE_MATCHED_KEY", "")
    if os.environ.get("CACHE_RESTORE_OUTCOME") != "success":
        status("restore-action-error; normal shell fallback")
        return
    if matched != identity():
        status("lookup-miss-or-unavailable; normal shell fallback")
        return
    status("restored-artifact")
    manifest, paths = read_manifest(artifacts)
    missing = [p for p in paths if subprocess.call(
        ["nix-store", "--check-validity", p], stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, timeout=TIMEOUT) != 0]
    required = sum(manifest["sizes"][p] for p in missing) + RESERVE
    require(shutil.disk_usage("/nix/store").free >= required, "store free-space budget")
    status(f"import producer={manifest['producer']['sha']} key={manifest['key']}")
    try:
        imported = import_archive(artifacts / "closure.nar.gz")
        require(sorted(imported) == paths, "imported path identities")
        run("nix-store", "--check-validity", *paths)
        run("nix-store", "--verify-path", *paths)
        require(path_sizes(paths) == manifest["sizes"], "registered NAR sizes")
    except Exception:
        status("partial-import-or-import-failed; registered prefixes retained, normal shell fallback")
        raise
    status("store-verified")
    run("nix", "develop", "--no-write-lock-file", "--max-jobs", "0",
        "--option", "substitute", "false", "--command", "true", cwd=ROOT)
    status("warm-store-ready (not a validation verdict or network-free claim)")


def realize():
    base, _ = owned()
    # This is mandatory even on a miss, rejected cache, or unsuccessful probe.
    # Linux CI: exec /proc/self/exe reuses the interpreter selected by Nix, even
    # when the captured build environment overwrites SHELL with builder Bash.
    # The trailing builtin keeps that interpreter alive while readlink runs.
    subprocess.run(["nix", "develop", "--no-write-lock-file", "--profile", str(base / "profile"),
                    "--command", "/proc/self/exe", "-c", 'readlink "/proc/$$/exe" > "$1"; :',
                    "bash", str(base / "shell")], cwd=ROOT, check=True, timeout=1800)
    status("current shell realized; validation commands still run fresh")


def can_save():
    return (os.environ.get("GITHUB_EVENT_NAME") == "push" and
            os.environ.get("GITHUB_REF") == "refs/heads/main" and
            os.environ.get("CACHE_MATCHED_KEY") != identity() and
            run("nix", "--version") == f"nix (Nix) {VERSION}")


def tooling():
    base, _ = owned()
    env = str((base / "profile").resolve(strict=True))
    shell = (base / "shell").read_text().strip()
    require(shell.endswith("/bin/bash"), "interactive Bash executable")
    roots = path_list(sorted(set((env, shell.removesuffix("/bin/bash")))))
    require(env.endswith("-env") and "-bash-interactive-" in roots[0] + roots[-1], "tooling roots")
    paths = path_list(sorted(set(run("nix-store", "--query", "--requisites", *roots).splitlines())))
    # Evaluate self.outPath only to prove that the repository source is NOT exported.
    source = run("nix", "eval", "--impure", "--raw", "--no-write-lock-file", "--expr",
                 "(builtins.getFlake (toString ./.)).outPath", cwd=ROOT)
    require(source not in paths, "repository source in selected closure; scope approval required")
    require(not any(p.endswith(".drv") or p.endswith("-source") for p in paths),
            "source/derivation closure; tooling-only export required")
    sizes = path_sizes(paths)
    sizes_valid(sizes, paths)
    status(f"tooling closure paths={len(paths)} nar_bytes={sum(sizes.values())} largest={max(sizes.values())} "
           f"roots={','.join(roots)} self_source_excluded=true derivations_excluded=true")
    return roots, paths, sizes


def export():
    base, artifacts = owned()
    output("ready", "false")
    roots, paths, sizes = tooling()  # PRs also check the tooling-only scope without publishing.
    if not can_save():
        status("save denied (not main push, version mismatch, or immutable exact entry exists)")
        return
    # Do not overwrite any restored bytes, including invalid or prefix entries.
    require(not list(artifacts.iterdir()), "nonempty staging; export skipped")
    require(shutil.disk_usage(artifacts).free >= MAX_ARCHIVE + RESERVE, "staging free-space budget")
    run("nix-store", "--check-validity", *paths)
    temporary = artifacts / "closure.tmp"
    decoded = 0
    start = time.monotonic()
    with subprocess.Popen(["nix-store", "--export", *paths], stdout=subprocess.PIPE,
                          preexec_fn=process_limits) as process:
        try:
            with temporary.open("wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=1, mtime=0) as compressed:
                for block in iter(lambda: process.stdout.read(1024 * 1024), b""):
                    decoded += len(block)
                    require(decoded <= MAX_NAR + MAX_METADATA * 4, "decoded export limit")
                    compressed.write(block)
                    compressed.flush()
                    require(raw.tell() <= MAX_ARCHIVE - 1024 * 1024, "compressed export limit")
            require(process.wait(timeout=TIMEOUT) == 0, "native export failed")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    archive = artifacts / "closure.nar.gz"
    temporary.rename(archive)
    (artifacts / "paths.txt").write_text("\n".join(paths) + "\n")
    manifest = dict(schema=SCHEMA, key=identity(), nix=VERSION, os=os.environ["RUNNER_OS"],
                    arch=os.environ["RUNNER_ARCH"], roots=roots, paths=len(paths), sizes=sizes,
                    producer=dict(repository=os.environ["GITHUB_REPOSITORY"], event="push",
                                  ref="refs/heads/main", sha=os.environ["GITHUB_SHA"]),
                    sha256=sha256(archive), archive_bytes=archive.stat().st_size, decoded_bytes=decoded)
    metadata = json.dumps(manifest, sort_keys=True) + "\n"
    require(len(metadata.encode()) <= MAX_METADATA, "metadata limit")
    (base / "manifest.tmp").write_text(metadata)
    (base / "manifest.tmp").rename(artifacts / "manifest.json")  # Publication marker last.
    read_manifest(artifacts)
    status(f"export-ready paths={len(paths)} nar_bytes={sum(sizes.values())} largest={max(sizes.values())} "
           f"compressed_bytes={archive.stat().st_size} seconds={time.monotonic() - start:.1f} "
           f"staging_free={shutil.disk_usage(artifacts).free} store_free={shutil.disk_usage('/nix/store').free}")
    output("ready", "true")


def cleanup():
    base, _ = owned()
    # Removing our profile releases its indirect root; no store paths or broad GC.
    shutil.rmtree(base)
    status("owned staging/profile removed")


def smoke():
    """Exercise unsigned registration via the installed daemon, not an existing path."""
    base, _ = owned()
    require(run("nix", "--version") == f"nix (Nix) {VERSION}", "smoke requires pinned Nix")
    require(os.environ.get("NIX_REMOTE", "daemon") == "daemon" and
            Path("/nix/var/nix/daemon-socket/socket").is_socket(), "daemon required")
    token = secrets.token_hex(16)
    name = f"smithplates-cache-smoke-{token}"
    expression = ('let f = builtins.getFlake (toString ./.); '
                  'p = import f.inputs.nixpkgs { system = builtins.currentSystem; }; '
                  f'in p.runCommand "{name}" {{}} "printf {token} > $out"')
    path = run("nix", "build", "--impure", "--no-link", "--print-out-paths", "--expr", expression, cwd=ROOT)
    require(PATH.fullmatch(path) and path.endswith("-" + name), "owned fixture path")
    require(Path(path).read_text() == token, "fixture contents")
    archive = base / "smoke.nar.gz"
    with archive.open("wb") as raw:
        data = subprocess.check_output(["nix-store", "--export", path], timeout=TIMEOUT)
        require(len(data) < MAX_METADATA, "tiny fixture size")
        raw.write(gzip.compress(data, mtime=0))
    # Delete exactly this newly created random fixture, never its dependencies.
    # Nix itself refuses deletion if another root/consumer acquired it.
    run("nix-store", "--delete", path)
    require(not Path(path).exists(), "fixture must be absent before import")
    try:
        require(import_archive(archive) == [path], "daemon roundtrip identity")
        run("nix-store", "--verify-path", path)
        require(Path(path).read_text() == token, "daemon roundtrip contents")
        status(f"real-daemon-unsigned-roundtrip passed Nix={VERSION} archive_bytes={archive.stat().st_size}")
    finally:
        if Path(path).exists():
            run("nix-store", "--delete", path)
        archive.unlink(missing_ok=True)


def alarm_handler(_signal, _frame):
    raise TimeoutError("cache operation wall-time limit")


def main():
    command = sys.argv[1]
    require(command in ("prepare", "restore", "realize", "export", "cleanup", "smoke"), "command")
    signal.signal(signal.SIGALRM, alarm_handler)
    signal.alarm(1800 if command == "realize" else TIMEOUT)
    try:
        globals()[command]()
    except Exception as error:
        if command not in ("restore", "export"):
            raise
        status(f"{command}-rejected-or-unavailable ({type(error).__name__}: {error}); normal shell remains mandatory")
    finally:
        signal.alarm(0)


if __name__ == "__main__":
    main()
