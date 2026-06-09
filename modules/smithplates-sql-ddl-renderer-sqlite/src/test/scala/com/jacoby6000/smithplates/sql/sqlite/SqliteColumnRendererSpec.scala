package com.jacoby6000.smithplates.sql.sqlite

import com.jacoby6000.smithplates.sql.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqliteColumnRendererSpec extends FunSuite {
  private val sqliteAutoUuidDefault: String =
    "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || " +
      "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"

  private def validateColumnRender(
      typeLabel: String,
      column: SqlColumn,
      requiredSuffix: String,
      nullableSuffix: String
  ): Unit = {
    test(s"$typeLabel - renders required columns") {
      assertEquals(
        SqliteRenderer.renderColumn(column.copy(nullable = false)),
        s"${column.name} $requiredSuffix"
      )
    }
    test(s"$typeLabel - renders nullable columns") {
      assertEquals(
        SqliteRenderer.renderColumn(column.copy(nullable = true)),
        s"${column.name} $nullableSuffix"
      )
    }
  }

  validateColumnRender(
    "Text",
    SqlColumn(name = "label", columnType = SqlColumnType.Text, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )
  validateColumnRender(
    "Integer",
    SqlColumn(name = "count", columnType = SqlColumnType.Integer, nullable = false),
    "INTEGER NOT NULL",
    "INTEGER"
  )
  validateColumnRender(
    "BigInt",
    SqlColumn(name = "size_bytes", columnType = SqlColumnType.BigInt, nullable = false),
    "BIGINT NOT NULL",
    "BIGINT"
  )
  validateColumnRender(
    "Boolean",
    SqlColumn(name = "active", columnType = SqlColumnType.Boolean, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )
  validateColumnRender(
    "Json",
    SqlColumn(name = "payload", columnType = SqlColumnType.Json, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )
  validateColumnRender(
    "Blob",
    SqlColumn(name = "data", columnType = SqlColumnType.Blob, nullable = false),
    "BLOB NOT NULL",
    "BLOB"
  )
  validateColumnRender(
    "Uuid",
    SqlColumn(name = "owner_id", columnType = SqlColumnType.Uuid, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )

  validateColumnRender(
    "Varchar",
    SqlColumn(name = "code", columnType = SqlColumnType.Varchar(maxLength = 64), nullable = false),
    "TEXT NOT NULL CHECK(length(code) <= 64)",
    "TEXT CHECK(length(code) <= 64)"
  )

  validateColumnRender(
    "StringEnum",
    SqlColumn(
      name = "direction",
      columnType = SqlColumnType.StringEnum(
        shapeId = ShapeId.from("example#Direction"),
        typeName = "example_direction",
        values = List("NORTH", "SOUTH")
      ),
      nullable = false
    ),
    "TEXT NOT NULL CHECK(direction IN ('NORTH', 'SOUTH'))",
    "TEXT CHECK(direction IN ('NORTH', 'SOUTH'))"
  )

  validateColumnRender(
    "IntEnum",
    SqlColumn(
      name = "status",
      columnType = SqlColumnType.IntEnum(typeName = "example_httpstatus", values = List(404, 200)),
      nullable = false
    ),
    "INTEGER NOT NULL CHECK(status IN (404, 200))",
    "INTEGER CHECK(status IN (404, 200))"
  )

  validateColumnRender(
    "AutoUuid",
    SqlColumn(
      name = "id",
      columnType = SqlColumnType.Uuid,
      nullable = false,
      autoGeneration = Some(SqlAutoUuid)
    ),
    s"TEXT NOT NULL DEFAULT $sqliteAutoUuidDefault",
    s"TEXT DEFAULT $sqliteAutoUuidDefault"
  )

  validateColumnRender(
    "Timestamp (date-time)",
    SqlColumn(
      name = "updated_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false
    ),
    "TEXT NOT NULL",
    "TEXT"
  )

  validateColumnRender(
    "Timestamp (epoch-seconds)",
    SqlColumn(
      name = "occurred_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false
    ),
    "REAL NOT NULL",
    "REAL"
  )

  validateColumnRender(
    "CreatedTimestamp (date-time)",
    SqlColumn(
      name = "created_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    ),
    "TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP",
    "TEXT DEFAULT CURRENT_TIMESTAMP"
  )

  validateColumnRender(
    "CreatedTimestamp (epoch-seconds)",
    SqlColumn(
      name = "recorded_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    ),
    "REAL NOT NULL DEFAULT unixepoch('subsec')",
    "REAL DEFAULT unixepoch('subsec')"
  )
}
