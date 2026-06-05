package com.jacoby6000.smithy.stache.sql.postgres

import com.jacoby6000.smithy.stache.sql.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class PostgresColumnRendererSpec extends FunSuite {
  private def validateColumnRender(
      typeLabel: String,
      column: SqlColumn,
      requiredSuffix: String,
      nullableSuffix: String
  ): Unit = {
    test(s"$typeLabel - renders required columns") {
      assertEquals(
        PostgresRenderer.renderColumn(column.copy(nullable = false)),
        s"${column.name} $requiredSuffix"
      )
    }
    test(s"$typeLabel - renders nullable columns") {
      assertEquals(
        PostgresRenderer.renderColumn(column.copy(nullable = true)),
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
    "BOOLEAN NOT NULL",
    "BOOLEAN"
  )
  validateColumnRender(
    "Json",
    SqlColumn(name = "payload", columnType = SqlColumnType.Json, nullable = false),
    "JSONB NOT NULL",
    "JSONB"
  )
  validateColumnRender(
    "Blob",
    SqlColumn(name = "data", columnType = SqlColumnType.Blob, nullable = false),
    "BYTEA NOT NULL",
    "BYTEA"
  )
  validateColumnRender(
    "Uuid",
    SqlColumn(name = "owner_id", columnType = SqlColumnType.Uuid, nullable = false),
    "UUID NOT NULL",
    "UUID"
  )

  validateColumnRender(
    "Varchar",
    SqlColumn(name = "code", columnType = SqlColumnType.Varchar(maxLength = 64), nullable = false),
    "VARCHAR(64) NOT NULL",
    "VARCHAR(64)"
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
    "example_direction NOT NULL",
    "example_direction"
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
    "UUID NOT NULL DEFAULT gen_random_uuid()",
    "UUID DEFAULT gen_random_uuid()"
  )

  validateColumnRender(
    "Timestamp (date-time)",
    SqlColumn(
      name = "created_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    ),
    "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP",
    "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
  )

  validateColumnRender(
    "Timestamp (epoch-seconds)",
    SqlColumn(
      name = "occurred_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    ),
    "DECIMAL(13, 3) NOT NULL DEFAULT ROUND(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::numeric, 3)",
    "DECIMAL(13, 3) DEFAULT ROUND(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::numeric, 3)"
  )
}
