package com.jacoby6000.smithplates.sql.ddl.renderer.sqlite

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class SqliteColumnRendererSpec extends FunSuite {
  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Text",
    SqlColumn(name = "label", columnType = SqlColumnType.Text, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )
  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Integer",
    SqlColumn(name = "count", columnType = SqlColumnType.Integer, nullable = false),
    "INTEGER NOT NULL",
    "INTEGER"
  )
  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "BigInt",
    SqlColumn(name = "size_bytes", columnType = SqlColumnType.BigInt, nullable = false),
    "BIGINT NOT NULL",
    "BIGINT"
  )
  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Boolean",
    SqlColumn(name = "active", columnType = SqlColumnType.Boolean, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )
  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Json",
    SqlColumn(name = "payload", columnType = SqlColumnType.Json, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )
  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Blob",
    SqlColumn(name = "data", columnType = SqlColumnType.Blob, nullable = false),
    "BLOB NOT NULL",
    "BLOB"
  )
  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Uuid",
    SqlColumn(name = "owner_id", columnType = SqlColumnType.Uuid, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Varchar",
    SqlColumn(name = "code", columnType = SqlColumnType.Varchar(maxLength = 64), nullable = false),
    "TEXT NOT NULL CHECK(length(code) <= 64)",
    "TEXT CHECK(length(code) <= 64)"
  )

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
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

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "IntEnum",
    SqlColumn(
      name = "status",
      columnType = SqlColumnType.IntEnum(typeName = "example_httpstatus", values = List(404, 200)),
      nullable = false
    ),
    "INTEGER NOT NULL CHECK(status IN (404, 200))",
    "INTEGER CHECK(status IN (404, 200))"
  )

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "AutoUuid",
    SqlColumn(
      name = "id",
      columnType = SqlColumnType.Uuid,
      nullable = false,
      autoGeneration = Some(SqlAutoUuid)
    ),
    s"TEXT NOT NULL DEFAULT ${SqliteColumnRendererSpec.internal.sqliteAutoUuidDefault}",
    s"TEXT DEFAULT ${SqliteColumnRendererSpec.internal.sqliteAutoUuidDefault}"
  )

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Timestamp (date-time)",
    SqlColumn(
      name = "updated_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false
    ),
    "TEXT NOT NULL",
    "TEXT"
  )

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
    "Timestamp (epoch-seconds)",
    SqlColumn(
      name = "occurred_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false
    ),
    "REAL NOT NULL",
    "REAL"
  )

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
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

  SqliteColumnRendererSpec.internal.validateColumnRender(
    this,
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

object SqliteColumnRendererSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val sqliteAutoUuidDefault: String =
      "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || " +
        "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"

    def validateColumnRender(
        suite: FunSuite,
        typeLabel: String,
        column: SqlColumn,
        requiredSuffix: String,
        nullableSuffix: String
    ): Unit = {
      suite.test(s"$typeLabel - renders required columns") {
        suite.assertEquals(
          SqliteRenderer.renderColumn(column.copy(nullable = false)),
          s"${column.name} $requiredSuffix"
        )
      }
      suite.test(s"$typeLabel - renders nullable columns") {
        suite.assertEquals(
          SqliteRenderer.renderColumn(column.copy(nullable = true)),
          s"${column.name} $nullableSuffix"
        )
      }
    }
  }
}
