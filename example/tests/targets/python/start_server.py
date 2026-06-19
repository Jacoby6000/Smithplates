#!/usr/bin/env python3
"""Start the Python petstore reference server for cross-language HTTP tests."""

from __future__ import annotations

import argparse
import json
import os
import signal
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Any

import httpx

REPO_ROOT = Path(__file__).resolve().parents[4]
EXAMPLE_PYTHON = REPO_ROOT / "example" / "python"
SRC_ROOT = EXAMPLE_PYTHON / "src"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("context_file", type=Path)
    parser.add_argument("pid_file", type=Path)
    return parser.parse_args()


def pick_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def seed_database(database_path: Path) -> dict[str, str]:
    pythonpath_entries = [str(SRC_ROOT)]
    existing = os.environ.get("PYTHONPATH")
    if existing:
        pythonpath_entries.append(existing)
    sys.path[:0] = pythonpath_entries

    from server.repository_service import PetstoreRepositoryService  # noqa: PLC0415
    from server.database import repository_lifespan  # noqa: PLC0415

    import asyncio

    async def bootstrap() -> dict[str, str]:
        async with repository_lifespan(database_path) as repositories:
            repository_service = PetstoreRepositoryService(repositories)
            _store_id, category_id = await repository_service.seed_reference_data()
            order_ids = await repository_service.seed_fulfillment_orders()
            return {
                "seed_category_id": category_id,
                "order_pending_id": order_ids["pending"],
                "order_shipped_id": order_ids["shipped"],
                "order_delivered_id": order_ids["delivered"],
            }

    return asyncio.run(bootstrap())


def wait_for_health(base_url: str, timeout_seconds: float = 30.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            response = httpx.get(f"{base_url}/health", timeout=2.0)
            if response.status_code == 200:
                return
        except httpx.HTTPError as error:
            last_error = error
        time.sleep(0.1)
    if last_error is not None:
        raise RuntimeError(f"server did not become healthy: {last_error}") from last_error
    raise RuntimeError("server did not become healthy before timeout")


def write_context(context_file: Path, payload: dict[str, Any]) -> None:
    context_file.parent.mkdir(parents=True, exist_ok=True)
    context_file.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    port = pick_free_port()
    base_url = f"http://127.0.0.1:{port}"

    database_path = Path(tempfile.mkdtemp(prefix="smithystache-example-tests-")) / "petstore.sqlite3"
    seed_variables = seed_database(database_path)

    env = os.environ.copy()
    env["PYTHONPATH"] = str(SRC_ROOT)
    env["PETSTORE_DATABASE_PATH"] = str(database_path)

    process = subprocess.Popen(
        [
            "uv",
            "run",
            "uvicorn",
            "server.app:app",
            "--host",
            "127.0.0.1",
            "--port",
            str(port),
            "--log-level",
            "warning",
        ],
        cwd=EXAMPLE_PYTHON,
        env=env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )

    args.pid_file.parent.mkdir(parents=True, exist_ok=True)
    args.pid_file.write_text(str(process.pid), encoding="utf-8")

    try:
        wait_for_health(base_url)
    except Exception:
        os.killpg(process.pid, signal.SIGTERM)
        raise

    write_context(
        args.context_file,
        {
            "base_url": base_url,
            "variables": seed_variables,
        },
    )
    print(base_url)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
