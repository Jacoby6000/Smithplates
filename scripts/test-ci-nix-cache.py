#!/usr/bin/env python3
"""Cheap production-helper tests. Subprocess stubs are NOT daemon/warm-cache proof."""

import contextlib
import copy
import gzip
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("cache", Path(__file__).with_name("ci-nix-cache.py"))
cache = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache)


class CacheTests(unittest.TestCase):
    def setUp(self):
        self.logs = contextlib.redirect_stdout(io.StringIO())
        self.logs.__enter__()  # Never label mocked imports as actual warm-store proof in CI logs.
        self.temporary = tempfile.TemporaryDirectory(prefix="smithplates-cache-test-", dir=os.environ.get("RUNNER_TEMP"))
        self.parent = Path(self.temporary.name)
        self.environment = patch.dict(os.environ, {
            "RUNNER_TEMP": str(self.parent), "RUNNER_OS": "Linux", "RUNNER_ARCH": "X64",
            "GITHUB_REPOSITORY": "owner/repo", "GITHUB_EVENT_NAME": "pull_request",
            "GITHUB_REF": "refs/pull/1/merge", "GITHUB_SHA": "a" * 40,
            "GITHUB_OUTPUT": str(self.parent / "output"), "GITHUB_ENV": str(self.parent / "env"),
            "CACHE_MATCHED_KEY": "", "CACHE_RESTORE_OUTCOME": "success",
        })
        self.environment.start()
        with patch.object(cache, "run", return_value=f"nix (Nix) {cache.VERSION}"):
            cache.prepare()
        os.environ["SMITHPLATES_NIX_CACHE_OWNER"] = (cache.directory() / "owner").read_text()
        self.base, self.artifacts = cache.owned()
        self.paths = ["/nix/store/" + "0" * 32 + "-bash-interactive-5.2",
                      "/nix/store/" + "1" * 32 + "-smithplates-env"]

    def tearDown(self):
        self.environment.stop()
        self.temporary.cleanup()
        self.logs.__exit__(None, None, None)

    def fixture(self, data=b"not a NAR: native parser must reject this"):
        archive = self.artifacts / "closure.nar.gz"
        archive.write_bytes(gzip.compress(data, mtime=0))
        (self.artifacts / "paths.txt").write_text("\n".join(self.paths) + "\n")
        self.manifest = dict(schema=cache.SCHEMA, key=cache.identity(), nix=cache.VERSION,
                             os="Linux", arch="X64", producer=dict(repository="owner/repo",
                             event="push", ref="refs/heads/main", sha="a" * 40), roots=self.paths,
                             paths=2, sizes=dict.fromkeys(self.paths, 100),
                             sha256=cache.sha256(archive), archive_bytes=archive.stat().st_size,
                             decoded_bytes=len(data))
        self.write_manifest()

    def write_manifest(self):
        (self.artifacts / "manifest.json").write_text(json.dumps(self.manifest))

    def reject(self):
        with self.assertRaises((ValueError, OSError, EOFError, KeyError, TypeError)):
            cache.read_manifest(self.artifacts)

    def test_key_deterministic_and_protocol_inputs(self):
        with tempfile.TemporaryDirectory(dir=self.parent) as temporary:
            root = Path(temporary)
            for name in cache.INPUTS:
                (root / name).parent.mkdir(parents=True, exist_ok=True)
                (root / name).write_text(name)
            with patch.object(cache, "ROOT", root):
                original = cache.identity()
                self.assertEqual(original, cache.identity())
                for name in cache.INPUTS:
                    target = root / name
                    target.write_text(name + "changed")
                    self.assertNotEqual(original, cache.identity(), name)
                    target.write_text(name)
                (root / "flake.lock").unlink()
                with self.assertRaises(FileNotFoundError):
                    cache.identity()

    def test_key_platform_repository_version_schema(self):
        original = cache.identity()
        for field, value in (("RUNNER_ARCH", "ARM64"), ("RUNNER_OS", "macOS"),
                             ("GITHUB_REPOSITORY", "owner/other")):
            with patch.dict(os.environ, {field: value}):
                self.assertNotEqual(original, cache.identity())
        for field in ("VERSION", "SCHEMA"):
            with patch.object(cache, field, "changed"):
                self.assertNotEqual(original, cache.identity())

    def test_version_mismatch_disables_only_cache(self):
        cache.cleanup()
        with patch.object(cache, "run", return_value="nix (Nix) 2.35.2"):
            cache.prepare()
        self.assertIn("enabled=false", (self.parent / "output").read_text())

    def test_metadata_identities(self):
        self.fixture()
        baseline = copy.deepcopy(self.manifest)
        for field, value in (("schema", "legacy"), ("key", "prefix"), ("nix", "2.35.2"),
                             ("os", "macOS"), ("arch", "ARM64"), ("roots", self.paths[:1]),
                             ("paths", 3), ("decoded_bytes", 1), ("archive_bytes", 1)):
            self.manifest = dict(baseline, **{field: value})
            self.write_manifest()
            self.reject()
        for field, value in (("event", "pull_request"), ("ref", "refs/heads/other"),
                             ("sha", "invalid"), ("repository", "owner/other")):
            self.manifest = copy.deepcopy(baseline)
            self.manifest["producer"][field] = value
            self.write_manifest()
            self.reject()

    def test_missing_extra_symlink_and_metadata_size(self):
        self.fixture()
        extra = self.artifacts / "extra"
        extra.touch()
        self.reject()
        extra.unlink()
        path = self.artifacts / "paths.txt"
        path.unlink()
        self.reject()
        path.symlink_to(self.parent / "output")
        self.reject()
        path.unlink()
        path.write_bytes(b"x" * (cache.MAX_METADATA + 1))
        self.reject()

    def test_path_traversal_duplicates_count_and_roots(self):
        self.fixture()
        for paths in ([self.paths[0], self.paths[0]], ["/etc/passwd"],
                      [self.paths[0] + "/../../etc"], list(reversed(self.paths)), []):
            (self.artifacts / "paths.txt").write_text("\n".join(paths) + "\n")
            self.reject()
        with patch.object(cache, "MAX_PATHS", 1):
            with self.assertRaises(ValueError):
                cache.path_list(self.paths)

    def test_per_path_sum_compressed_and_decoded_budgets(self):
        self.fixture()
        for field, limit in (("MAX_PATH", 99), ("MAX_NAR", 199), ("MAX_ARCHIVE", 1)):
            with patch.object(cache, field, limit):
                self.reject()
        self.manifest["sizes"][self.paths[0]] = True
        self.write_manifest()
        self.reject()
        self.fixture(b"x" * 1024)
        with patch.object(cache, "MAX_NAR", 200), patch.object(cache, "MAX_METADATA", 1000):
            # The native stream overhead budget is explicit, not an unbounded inflate.
            self.fixture(b"x" * 5000)
            self.reject()

    def test_checksum_truncation_and_invalid_gzip(self):
        self.fixture()
        archive = self.artifacts / "closure.nar.gz"
        archive.write_bytes(archive.read_bytes()[:-4])
        self.reject()
        self.manifest.update(sha256=cache.sha256(archive), archive_bytes=archive.stat().st_size)
        self.write_manifest()
        self.reject()
        archive.write_bytes(b"invalid gzip")
        self.manifest.update(sha256=cache.sha256(archive), archive_bytes=archive.stat().st_size)
        self.write_manifest()
        self.reject()

    def test_recomputed_manifest_is_not_authentication(self):
        # Metadata/gzip checks deliberately cannot authenticate even native-valid NARs.
        # Production still invokes the Nix parser; producer exclusion is GitHub ACL policy.
        self.fixture(b"attacker-controlled payload")
        cache.read_manifest(self.artifacts)
        self.fixture(b"different attacker-controlled payload")
        cache.read_manifest(self.artifacts)

    def test_exact_hit_only_and_backend_error(self):
        for matched, outcome in (("", "success"), (cache.identity() + "-suffix", "success"),
                                 (cache.identity(), "failure")):
            with patch.dict(os.environ, {"CACHE_MATCHED_KEY": matched, "CACHE_RESTORE_OUTCOME": outcome}), \
                    patch.object(cache, "import_archive") as importer:
                cache.restore()
                importer.assert_not_called()

    def test_restore_runs_production_import_validity_and_probe(self):
        self.fixture()
        with patch.dict(os.environ, {"CACHE_MATCHED_KEY": cache.identity()}), \
                patch.object(cache, "import_archive", return_value=self.paths) as importer, \
                patch.object(cache, "run", return_value="") as runner, \
                patch.object(cache, "path_sizes", return_value=self.manifest["sizes"]), \
                patch.object(cache.subprocess, "call", return_value=0), \
                patch.object(cache.shutil, "disk_usage", return_value=type("Disk", (), {"free": cache.MAX_NAR + cache.RESERVE})()):
            cache.restore()
            importer.assert_called_once_with(self.artifacts / "closure.nar.gz")
            calls = [call.args for call in runner.call_args_list]
            self.assertIn(("nix-store", "--verify-path", *self.paths), calls)
            self.assertIn(("nix", "develop", "--no-write-lock-file", "--max-jobs", "0",
                           "--option", "substitute", "false", "--command", "true"), calls)

    def test_partial_import_does_not_probe_or_delete(self):
        self.fixture()
        with patch.dict(os.environ, {"CACHE_MATCHED_KEY": cache.identity()}), \
                patch.object(cache, "import_archive", side_effect=ValueError("partial native failure")), \
                patch.object(cache, "run") as runner, patch.object(cache.subprocess, "call", return_value=0), \
                patch.object(cache.shutil, "disk_usage", return_value=type("Disk", (), {"free": cache.RESERVE})()):
            with self.assertRaises(ValueError):
                cache.restore()
            runner.assert_not_called()

    def test_native_import_boundary_failure_and_time_limit(self):
        self.fixture()
        binary = self.parent / "bin"
        binary.mkdir()
        stub = binary / "nix-store"
        stub.write_text("#!/bin/sh\ncat >/dev/null\nexit 1\n")
        stub.chmod(0o700)
        with patch.dict(os.environ, {"PATH": str(binary) + ":" + os.environ["PATH"]}):
            with self.assertRaises(ValueError):
                cache.import_archive(self.artifacts / "closure.nar.gz")
        with self.assertRaises(TimeoutError):
            cache.alarm_handler(None, None)

    def test_native_import_boundary_identity_output(self):
        self.fixture()
        binary = self.parent / "bin"
        binary.mkdir()
        stub = binary / "nix-store"
        stub.write_text("#!/bin/sh\ncat >/dev/null\nprintf '%s\\n' " + " ".join(self.paths) + "\n")
        stub.chmod(0o700)
        with patch.dict(os.environ, {"PATH": str(binary) + ":" + os.environ["PATH"]}):
            self.assertEqual(cache.import_archive(self.artifacts / "closure.nar.gz"), self.paths)

    def test_export_stream_atomic_manifest_and_failure(self):
        binary = self.parent / "bin"
        binary.mkdir()
        stub = binary / "nix-store"
        stub.write_text("#!/bin/sh\nprintf 'stub-native-export-stream'\n")
        stub.chmod(0o700)
        environment = dict(GITHUB_EVENT_NAME="push", GITHUB_REF="refs/heads/main",
                           PATH=str(binary) + ":" + os.environ["PATH"])
        sizes = dict.fromkeys(self.paths, 100)
        with patch.dict(os.environ, environment), patch.object(cache, "can_save", return_value=True), \
                patch.object(cache, "tooling", return_value=(self.paths, self.paths, sizes)), \
                patch.object(cache, "run", return_value=""), \
                patch.object(cache.shutil, "disk_usage", return_value=type("Disk", (), {"free": cache.MAX_NAR + cache.RESERVE})()):
            cache.export()
            manifest, paths = cache.read_manifest(self.artifacts)
            self.assertEqual(paths, self.paths)
            self.assertEqual(manifest["decoded_bytes"], len(b"stub-native-export-stream"))
            self.assertEqual({p.name for p in self.artifacts.iterdir()}, cache.FILES)
            before = (self.artifacts / "manifest.json").read_bytes()
            with self.assertRaises(ValueError):
                cache.export()  # Even a non-exact restored entry is never overwritten.
            self.assertEqual((self.artifacts / "manifest.json").read_bytes(), before)
            for path in self.artifacts.iterdir():
                path.unlink()
            stub.write_text("#!/bin/sh\nprintf 'partial'\nexit 1\n")
            with self.assertRaises(ValueError):
                cache.export()
            self.assertFalse((self.artifacts / "manifest.json").exists())
            self.assertTrue((self.parent / "output").read_text().endswith("ready=false\n"))

    def test_tooling_rejects_self_source_and_derivations(self):
        (self.base / "shell").write_text(self.paths[0] + "/bin/bash")
        source = "/nix/store/" + "2" * 32 + "-source"
        for unwanted in (source, "/nix/store/" + "3" * 32 + "-tools.drv"):
            with patch.object(cache.Path, "resolve", return_value=Path(self.paths[1])), \
                    patch.object(cache, "run", side_effect=["\n".join(sorted(self.paths + [unwanted])), source]):
                with self.assertRaises(ValueError):
                    cache.tooling()

    def test_fallback_mandatory_and_failure_not_swallowed(self):
        with patch.object(cache.subprocess, "run", side_effect=subprocess.CalledProcessError(1, "nix")) as runner:
            with self.assertRaises(subprocess.CalledProcessError):
                cache.realize()
            self.assertTrue(runner.call_args.kwargs["check"])
        action = (cache.ROOT / ".github/actions/setup-validate/action.yml").read_text()
        mandatory = action.split("- name: Realize current Nix shell (mandatory)")[1].split("- name:")[0]
        self.assertNotIn("continue-on-error", mandatory)
        self.assertIn("if: inputs.backend == 'nix'", mandatory)

    def test_save_only_main_push_and_no_immutable_replacement(self):
        with patch.object(cache, "run", return_value=f"nix (Nix) {cache.VERSION}"):
            for event, ref, matched, expected in (
                    ("pull_request", "refs/heads/main", "", False),
                    ("workflow_dispatch", "refs/heads/main", "", False),
                    ("push", "refs/heads/other", "", False),
                    ("push", "refs/heads/main", cache.identity(), False),
                    ("push", "refs/heads/main", "", True)):
                with patch.dict(os.environ, {"GITHUB_EVENT_NAME": event, "GITHUB_REF": ref, "CACHE_MATCHED_KEY": matched}):
                    self.assertEqual(cache.can_save(), expected)
        with patch.object(cache, "tooling", return_value=(self.paths, self.paths, {})):
            cache.export()  # PR may not publish, even if tooling is present.
        self.assertEqual(list(self.artifacts.iterdir()), [])

    def test_ownership_cleanup_preserves_foreign_files(self):
        foreign = self.parent / "foreign"
        foreign.write_text("keep")
        with self.assertRaises(FileExistsError):
            cache.prepare()
        with patch.dict(os.environ, {"SMITHPLATES_NIX_CACHE_OWNER": "wrong"}):
            with self.assertRaises(ValueError):
                cache.cleanup()
        cache.cleanup()
        self.assertEqual(foreign.read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
