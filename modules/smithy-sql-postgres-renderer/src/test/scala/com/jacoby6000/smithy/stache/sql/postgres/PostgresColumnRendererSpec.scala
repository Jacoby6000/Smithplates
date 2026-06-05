package com.jacoby6000.smithy.stache.sql.postgres

import com.jacoby6000.smithy.stache.sql.*
import munit.FunSuite
import software.amazon.smithy.model.shapes.ShapeId

final class PostgresColumnRendererSpec extends FunSuite {
  test("Text - renders nullable text columns") {
    val column = SqlColumn(name = "label", columnType = SqlColumnType.Text, nullable = true)

    assertEquals(PostgresRenderer.renderColumn(column), "label TEXT")
  }

  test("Integer - renders integer columns") {
    val column = SqlColumn(name = "count", columnType = SqlColumnType.Integer, nullable = true)

    assertEquals(PostgresRenderer.renderColumn(column), "count INTEGER")
  }

  test("BigInt - renders bigint columns") {
    val column = SqlColumn(name = "size_bytes", columnType = SqlColumnType.BigInt, nullable = true)

    assertEquals(PostgresRenderer.renderColumn(column), "size_bytes BIGINT")
  }

  test("Boolean - renders boolean columns") {
    val column = SqlColumn(name = "active", columnType = SqlColumnType.Boolean, nullable = false)

    assertEquals(PostgresRenderer.renderColumn(column), "active BOOLEAN NOT NULL")
  }

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

  test("Uuid - renders non-auto-generated uuid columns") {
    val column = SqlColumn(
      name = "owner_id",
      columnType = SqlColumnType.Uuid,
      nullable = true
    )

    assertEquals(PostgresRenderer.renderColumn(column), "owner_id UUID")
  }

  test("Json - renders json columns as JSONB") {
    val column = SqlColumn(name = "payload", columnType = SqlColumnType.Json, nullable = true)

    assertEquals(PostgresRenderer.renderColumn(column), "payload JSONB")
  }

  test("Blob - renders blob columns as BYTEA") {
    val column = SqlColumn(name = "data", columnType = SqlColumnType.Blob, nullable = true)

    assertEquals(PostgresRenderer.renderColumn(column), "data BYTEA")
  }

  test("Varchar - renders varchar columns with max length") {
    val column = SqlColumn(
      name = "code",
      columnType = SqlColumnType.Varchar(maxLength = 64),
      nullable = false
    )

    assertEquals(PostgresRenderer.renderColumn(column), "code VARCHAR(64) NOT NULL")
  }

  test("StringEnum - renders string enum columns with enum type name") {
    val column = SqlColumn(
      name = "direction",
      columnType = SqlColumnType.StringEnum(
        shapeId = ShapeId.from("example#Direction"),
        typeName = "example_direction",
        values = List("NORTH", "SOUTH")
      ),
      nullable = true
    )

    assertEquals(PostgresRenderer.renderColumn(column), "direction example_direction")
  }

  test("IntEnum - renders int enum columns with INTEGER storage when values fit") {
    val column = SqlColumn(
      name = "status",
      columnType = SqlColumnType.IntEnum(typeName = "example_httpstatus", values = List(404, 200)),
      nullable = false
    )

    assertEquals(
      PostgresRenderer.renderColumn(column),
      "status INTEGER NOT NULL CHECK(status IN (404, 200))"
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
