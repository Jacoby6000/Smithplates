"""Shared fixtures for generated SQL integration tests."""

from __future__ import annotations

import os
from collections.abc import Iterator
from contextlib import contextmanager
from uuid import uuid4

import psycopg
import pytest
from psycopg import sql
from psycopg.conninfo import make_conninfo


@contextmanager
def _postgres_server() -> Iterator[str]:
    configured = os.environ.get("SMITHPLATES_TEST_POSTGRES_DSN")
    if configured:
        yield configured
    else:
        from testcontainers.postgres import PostgresContainer

        with PostgresContainer("postgres:16-alpine", driver=None) as container:
            yield container.get_connection_url()


@pytest.fixture(scope="module")
def postgres_dsn() -> Iterator[str]:
    """Own a disposable database; never migrate the supplied service database."""
    with _postgres_server() as server_dsn, psycopg.connect(server_dsn, autocommit=True) as admin:
        name = "smithplates_test_" + uuid4().hex
        admin.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(name)))
        try:
            yield make_conninfo(server_dsn, dbname=name)
        finally:
            admin.execute(sql.SQL("DROP DATABASE {} WITH (FORCE)").format(sql.Identifier(name)))
