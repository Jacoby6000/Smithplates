package com.jacoby6000.smithy.stache.sql.postgres

import com.jacoby6000.smithy.stache.sql.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class PostgresColumnRendererSpec extends FunSuite {
  private def validateColumnRender(
      typeLabel: String,
      name: String,
      columnType: SqlColumnType,
      requiredSuffix: String,
      nullableSuffix: String
  ): Unit = {
    test(s"$typeLabel - renders required columns") {
      assertEquals(
        PostgresRenderer.renderColumn(SqlColumn(name = name, columnType = columnType, nullable = false)),
        s"$name $requiredSuffix"
      )
    }
    test(s"$typeLabel - renders nullable columns") {
      assertEquals(
        PostgresRenderer.renderColumn(SqlColumn(name = name, columnType = columnType, nullable = true)),
        s"$name $nullableSuffix"
      )
    }
  }

  validateColumnRender("Text", "label", SqlColumnType.Text, "TEXT NOT NULL", "TEXT")
  validateColumnRender("Integer", "count", SqlColumnType.Integer, "INTEGER NOT NULL", "INTEGER")
  validateColumnRender("BigInt", "size_bytes", SqlColumnType.BigInt, "BIGINT NOT NULL", "BIGINT")
  validateColumnRender("Boolean", "active", SqlColumnType.Boolean, "BOOLEAN NOT NULL", "BOOLEAN")
  validateColumnRender("Json", "payload", SqlColumnType.Json, "JSONB NOT NULL", "JSONB")
  validateColumnRender("Blob", "data", SqlColumnType.Blob, "BYTEA NOT NULL", "BYTEA")
  validateColumnRender("Uuid", "owner_id", SqlColumnType.Uuid, "UUID NOT NULL", "UUID")

  validateColumnRender(
    "Varchar",
    "code",
    SqlColumnType.Varchar(maxLength = 64),
    "VARCHAR(64) NOT NULL",
    "VARCHAR(64)"
  )

  validateColumnRender(
    "StringEnum",
    "direction",
    SqlColumnType.StringEnum(
      shapeId = ShapeId.from("example#Direction"),
      typeName = "example_direction",
      values = List("NORTH", "SOUTH")
    ),
    "example_direction NOT NULL",
    "example_direction"
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
      PostgresRenderer.renderColumn(column),
      "id UUID NOT NULL DEFAULT gen_random_uuid()"
    )
  }

  test("Timestamp - renders date-time columns as TIMESTAMP") {
    val column = SqlColumn(
      name = "created_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.DateTime),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    )

    assertEquals(
      PostgresRenderer.renderColumn(column),
      "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
    )
  }

  test("Timestamp - renders epoch-seconds columns as DECIMAL(13, 3)") {
    val column = SqlColumn(
      name = "occurred_at",
      columnType = SqlColumnType.Timestamp(SqlTimestampFormat.EpochSeconds),
      nullable = false,
      autoGeneration = Some(SqlCreatedTimestamp)
    )

    assertEquals(
      PostgresRenderer.renderColumn(column),
      "occurred_at DECIMAL(13, 3) NOT NULL DEFAULT ROUND(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::numeric, 3)"
    )
  }
}
