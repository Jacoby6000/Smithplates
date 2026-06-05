package com.jacoby6000.smithy.stache.sql.sqlite

import com.jacoby6000.smithy.stache.sql.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqliteColumnRendererSpec extends FunSuite {
  private val sqliteAutoUuidDefault: String =
    "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"

  private def validateColumnRender(
      typeLabel: String,
      name: String,
      columnType: SqlColumnType,
      requiredSuffix: String,
      nullableSuffix: String
  ): Unit = {
    test(s"$typeLabel - renders required columns") {
      assertEquals(
        SqliteRenderer.renderColumn(SqlColumn(name = name, columnType = columnType, nullable = false)),
        s"$name $requiredSuffix"
      )
    }
    test(s"$typeLabel - renders nullable columns") {
      assertEquals(
        SqliteRenderer.renderColumn(SqlColumn(name = name, columnType = columnType, nullable = true)),
        s"$name $nullableSuffix"
      )
    }
  }

  validateColumnRender("Text", "label", SqlColumnType.Text, "TEXT NOT NULL", "TEXT")
  validateColumnRender("Integer", "count", SqlColumnType.Integer, "INTEGER NOT NULL", "INTEGER")
  validateColumnRender("BigInt", "size_bytes", SqlColumnType.BigInt, "BIGINT NOT NULL", "BIGINT")
  validateColumnRender("Boolean", "active", SqlColumnType.Boolean, "TEXT NOT NULL", "TEXT")
  validateColumnRender("Json", "payload", SqlColumnType.Json, "TEXT NOT NULL", "TEXT")
  validateColumnRender("Blob", "data", SqlColumnType.Blob, "BLOB NOT NULL", "BLOB")
  validateColumnRender("Uuid", "owner_id", SqlColumnType.Uuid, "TEXT NOT NULL", "TEXT")

  validateColumnRender(
    "Varchar",
    "code",
    SqlColumnType.Varchar(maxLength = 64),
    "TEXT NOT NULL CHECK(length(code) <= 64)",
    "TEXT CHECK(length(code) <= 64)"
  )

  validateColumnRender(
    "StringEnum",
    "direction",
    SqlColumnType.StringEnum(
      shapeId = ShapeId.from("example#Direction"),
      typeName = "example_direction",
      values = List("NORTH", "SOUTH")
    ),
    "TEXT NOT NULL CHECK(direction IN ('NORTH', 'SOUTH'))",
    "TEXT CHECK(direction IN ('NORTH', 'SOUTH'))"
  )

  validateColumnRender(
    "IntEnum",
    "status",
    SqlColumnType.IntEnum(typeName = "example_httpstatus", values = List(404, 200)),
    "INTEGER NOT NULL CHECK(status IN (404, 200))",
    "INTEGER CHECK(status IN (404, 200))"
  )

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
