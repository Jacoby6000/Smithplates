#!/usr/bin/env python3

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

FENCE_BY_SUFFIX = {
    ".mmd": "mermaid",
}

SKIP_DIRS = {
    ".git",
    "target",
    "node_modules",
    ".venv",
}

REUSABLE_COMPONENTS_DIR = "docs/reusable-components"
SCRIPT_NAME = "scripts/sync_reusable_components.py"


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def fence_for(path: Path) -> str:
    fence = FENCE_BY_SUFFIX.get(path.suffix)
    if fence is None:
        supported = ", ".join(sorted(FENCE_BY_SUFFIX))
        print(
            f"Unsupported reusable component extension {path.suffix!r} for {path.name}; "
            f"supported: {supported}",
            file=sys.stderr,
        )
        sys.exit(1)
    return fence


def discover_components(reusable_root: Path) -> list[Path]:
    return sorted(
        path
        for path in reusable_root.iterdir()
        if path.is_file() and path.name != "README.md"
    )


def discover_targets(root: Path, reusable_root: Path, marker: str) -> list[Path]:
    marker_start = f"<!-- {marker}:start -->"
    targets: list[Path] = []

    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.is_relative_to(reusable_root):
            continue
        if path.suffix != ".md":
            continue

        try:
            text = path.read_text()
        except (OSError, UnicodeDecodeError):
            continue

        if marker_start in text:
            targets.append(path)

    return sorted(targets)


def embed_block(marker: str, fence: str, content: str) -> str:
    marker_start = f"<!-- {marker}:start -->"
    marker_end = f"<!-- {marker}:end -->"
    body = content.rstrip() + "\n"
    return f"{marker_start}\n```{fence}\n{body}```\n{marker_end}"


def sync_component(
    component: Path,
    root: Path,
    reusable_root: Path,
    *,
    check: bool,
) -> list[tuple[str, Path]]:
    marker = component.name
    fence = fence_for(component)
    targets = discover_targets(root, reusable_root, marker)
    if not targets:
        return []

    block = embed_block(marker, fence, component.read_text())
    marker_start = f"<!-- {marker}:start -->"
    marker_end = f"<!-- {marker}:end -->"
    pattern = re.compile(
        re.escape(marker_start) + r".*?" + re.escape(marker_end),
        re.DOTALL,
    )

    out_of_date: list[tuple[str, Path]] = []
    for target in targets:
        text = target.read_text()
        if not pattern.search(text):
            print(f"Missing {marker} markers in {target}", file=sys.stderr)
            sys.exit(1)

        updated = pattern.sub(block, text, count=1)
        if check:
            if updated != text:
                out_of_date.append((marker, target))
        else:
            target.write_text(updated)

    return out_of_date


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Embed reusable documentation components from docs/reusable-components/ "
            "into marked Markdown files."
        ),
        epilog=(
            "Components, fence types, and embed targets are discovered automatically:\n"
            "  - every file in docs/reusable-components/ except README.md is a component\n"
            "  - .mmd files use a mermaid fence\n"
            "  - the component filename is the marker (for example architecture-pipeline.mmd)\n"
            "  - targets are Markdown files containing <!-- <filename>:start -->"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if embedded component blocks are out of date",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    root = repo_root()
    reusable_root = root / REUSABLE_COMPONENTS_DIR

    if not reusable_root.is_dir():
        print(f"Missing reusable components root: {reusable_root}", file=sys.stderr)
        return 1

    out_of_date: list[tuple[str, Path]] = []
    for component in discover_components(reusable_root):
        out_of_date.extend(
            sync_component(component, root, reusable_root, check=args.check)
        )

    if out_of_date:
        for marker, target in out_of_date:
            print(
                f"Embedded component {marker} is out of date in {target}",
                file=sys.stderr,
            )
        print(
            f"Run {SCRIPT_NAME} to refresh embedded component blocks.",
            file=sys.stderr,
        )
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
