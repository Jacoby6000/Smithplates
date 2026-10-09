"""The example sync preserves generated SQL fixture ownership."""

import subprocess
from pathlib import Path

import pytest


@pytest.mark.parametrize("has_fixture", [False, True])
def test_sync_generated_root_fixture(tmp_path: Path, has_fixture: bool) -> None:
    root = Path(__file__).resolve().parents[3]
    example = tmp_path / "example"
    build = example / "build/smithy/source/smithplates"
    (build / "src/generated").mkdir(parents=True)
    (build / "tests/petstore").mkdir(parents=True)
    (example / "src").mkdir()
    (example / "tests").mkdir()
    owned_test = example / "tests/test_api.py"
    owned_test.write_text("example-owned HTTP setup")
    fixture = example / "tests/conftest.py"
    fixture.write_text("legacy fixture")
    generated_fixture = root / "templates/python/src/db/tests/conftest.py"
    if has_fixture:
        (build / "tests/conftest.py").write_bytes(generated_fixture.read_bytes())
    subprocess.run(
        [
            "bash",
            "-c",
            'source "$1"; sync_smithplates_output "$2"',
            "sync",
            str(root / "scripts/run-example-build.sh"),
            str(example),
        ],
        cwd=root,
        check=True,
    )
    assert owned_test.read_text() == "example-owned HTTP setup"
    assert fixture.read_bytes() == (generated_fixture.read_bytes() if has_fixture else b"legacy fixture")
