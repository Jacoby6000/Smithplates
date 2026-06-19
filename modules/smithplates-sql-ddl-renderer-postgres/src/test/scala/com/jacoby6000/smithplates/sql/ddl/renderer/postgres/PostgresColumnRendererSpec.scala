package com.jacoby6000.smithplates.sql.ddl.renderer.postgres

import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class PostgresColumnRendererSpec extends FunSuite {
  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Text",
    SqlColumn(name = "label", columnType = SqlColumnType.Text, nullable = false),
    "TEXT NOT NULL",
    "TEXT"
  )
  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Integer",
    SqlColumn(name = "count", columnType = SqlColumnType.Integer, nullable = false),
    "INTEGER NOT NULL",
    "INTEGER"
  )
  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "BigInt",
    SqlColumn(name = "size_bytes", columnType = SqlColumnType.BigInt, nullable = false),
    "BIGINT NOT NULL",
    "BIGINT"
  )
  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Boolean",
    SqlColumn(name = "active", columnType = SqlColumnType.Boolean, nullable = false),
    "BOOLEAN NOT NULL",
    "BOOLEAN"
  )
  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Json",
    SqlColumn(name = "payload", columnType = SqlColumnType.Json, nullable = false),
    "JSONB NOT NULL",
    "JSONB"
  )
  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Blob",
    SqlColumn(name = "data", columnType = SqlColumnType.Blob, nullable = false),
    "BYTEA NOT NULL",
    "BYTEA"
  )
  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Uuid",
    SqlColumn(name = "owner_id", columnType = SqlColumnType.Uuid, nullable = false),
    "UUID NOT NULL",
    "UUID"
  )

  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Varchar",
    SqlColumn(name = "code", columnType = SqlColumnType.Varchar(maxLength = 64), nullable = false),
    "VARCHAR(64) NOT NULL",
    "VARCHAR(64)"
  )

  PostgresColumnRendererSpec.internal.validateColumnRender(
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
    "example_direction NOT NULL",
    "example_direction"
  )

  PostgresColumnRendererSpec.internal.validateColumnRender(
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

  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
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

  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Timestamp (date-time)",
    SqlColumn(
      name = "updated_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false
    ),
    "TIMESTAMP NOT NULL",
    "TIMESTAMP"
  )

  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "Timestamp (epoch-seconds)",
    SqlColumn(
      name = "occurred_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false
    ),
    "DECIMAL(13, 3) NOT NULL",
    "DECIMAL(13, 3)"
  )

  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "CreatedTimestamp (date-time)",
    SqlColumn(
      name = "created_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    ),
    "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP",
    "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
  )

  PostgresColumnRendererSpec.internal.validateColumnRender(
    this,
    "CreatedTimestamp (epoch-seconds)",
    SqlColumn(
      name = "recorded_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    ),
    "DECIMAL(13, 3) NOT NULL DEFAULT ROUND(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::numeric, 3)",
    "DECIMAL(13, 3) DEFAULT ROUND(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::numeric, 3)"
  )
}

object PostgresColumnRendererSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def validateColumnRender(
        suite: FunSuite,
        typeLabel: String,
        column: SqlColumn,
        requiredSuffix: String,
        nullableSuffix: String
    ): Unit = {
      suite.test(s"$typeLabel - renders required columns") {
        suite.assertEquals(
          PostgresRenderer.renderColumn(column.copy(nullable = false)),
          s"${column.name} $requiredSuffix"
        )
      }
      suite.test(s"$typeLabel - renders nullable columns") {
        suite.assertEquals(
          PostgresRenderer.renderColumn(column.copy(nullable = true)),
          s"${column.name} $nullableSuffix"
        )
      }
    }
  }
}
