package com.jacoby6000.smithy.stache.sql.sqlite

import com.jacoby6000.smithy.stache.sql.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqliteColumnRendererSpec extends FunSuite {
  private val sqliteAutoUuidDefault: String =
    "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"

  test("Text - renders nullable text columns") {
    val column = SqlColumn(name = "label", columnType = SqlColumnType.Text, nullable = true)

    assertEquals(SqliteRenderer.renderColumn(column), "label TEXT")
  }

  test("Integer - renders integer columns") {
    val column = SqlColumn(name = "count", columnType = SqlColumnType.Integer, nullable = true)

    assertEquals(SqliteRenderer.renderColumn(column), "count INTEGER")
  }

  test("BigInt - renders bigint columns") {
    val column = SqlColumn(name = "size_bytes", columnType = SqlColumnType.BigInt, nullable = true)

    assertEquals(SqliteRenderer.renderColumn(column), "size_bytes BIGINT")
  }

  test("Boolean - renders boolean columns as TEXT") {
    val column = SqlColumn(name = "active", columnType = SqlColumnType.Boolean, nullable = false)

    assertEquals(SqliteRenderer.renderColumn(column), "active TEXT NOT NULL")
  }

  test("Uuid - renders auto-generated uuid columns") {
    val column = SqlColumn(
      name = "id",
      columnType = SqlColumnType.Uuid,
      nullable = false,
      autoGeneration = Some(SqlAutoUuid)
    )

    assertEquals(
      SqliteRenderer.renderColumn(column),
      s"id TEXT NOT NULL DEFAULT $sqliteAutoUuidDefault"
    )
  }

  test("Uuid - renders non-auto-generated uuid columns") {
    val column = SqlColumn(
      name = "owner_id",
      columnType = SqlColumnType.Uuid,
      nullable = true
    )

    assertEquals(SqliteRenderer.renderColumn(column), "owner_id TEXT")
  }

  test("Json - renders json columns as TEXT") {
    val column = SqlColumn(name = "payload", columnType = SqlColumnType.Json, nullable = true)

    assertEquals(SqliteRenderer.renderColumn(column), "payload TEXT")
  }

  test("Blob - renders blob columns as BLOB") {
    val column = SqlColumn(name = "data", columnType = SqlColumnType.Blob, nullable = true)

    assertEquals(SqliteRenderer.renderColumn(column), "data BLOB")
  }

  test("Varchar - renders varchar columns with length check") {
    val column = SqlColumn(
      name = "code",
      columnType = SqlColumnType.Varchar(maxLength = 64),
      nullable = false
    )

    assertEquals(
      SqliteRenderer.renderColumn(column),
      "code TEXT NOT NULL CHECK(length(code) <= 64)"
    )
  }

  test("StringEnum - renders string enum columns with value check") {
    val column = SqlColumn(
      name = "direction",
      columnType = SqlColumnType.StringEnum(
        shapeId = ShapeId.from("example#Direction"),
        typeName = "example_direction",
        values = List("NORTH", "SOUTH")
      ),
      nullable = true
    )

    assertEquals(
      SqliteRenderer.renderColumn(column),
      "direction TEXT CHECK(direction IN ('NORTH', 'SOUTH'))"
    )
  }

  test("IntEnum - renders int enum columns with INTEGER storage and value check") {
    val column = SqlColumn(
      name = "status",
      columnType = SqlColumnType.IntEnum(typeName = "example_httpstatus", values = List(404, 200)),
      nullable = false
    )

    assertEquals(
      SqliteRenderer.renderColumn(column),
      "status INTEGER NOT NULL CHECK(status IN (404, 200))"
    )
  }

  test("Timestamp - renders date-time columns as TEXT") {
    val column = SqlColumn(
      name = "created_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    )

    assertEquals(
      SqliteRenderer.renderColumn(column),
      "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
    )
  }

  test("Timestamp - renders epoch-seconds columns as REAL") {
    val column = SqlColumn(
      name = "occurred_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    )

    assertEquals(
      SqliteRenderer.renderColumn(column),
      "occurred_at REAL NOT NULL DEFAULT unixepoch('subsec')"
    )
  }
}
