package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.sql.ddl.renderer.sqlite.SqliteRenderer
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.renderer.SqlSchemaHash.sha256Hex
import software.amazon.smithy.model.shapes.ShapeId

class SqlCodegenMigrationBuilderSpec extends munit.FunSuite {
  private val bookmarkTable =
    SqlTable(
      name = "bookmarks",
      shapeId = ShapeId.from("example#Bookmark"),
      columns = List(
        SqlColumn(
          name = "id",
          columnType = SqlColumnType.Text,
          nullable = false,
          autoGeneration = Some(SqlAutoUuid)
        ),
        SqlColumn(name = "title", columnType = SqlColumnType.Text, nullable = true)
      ),
      primaryKeys = List("id"),
      foreignKeys = Nil,
      indexes = Nil
    )

  private val schema = SqlSchema(tables = List(bookmarkTable))

  test("build initial migration metadata from full schema DDL") {
    val migration =
      SqlCodegenMigrationBuilder
        .build(schema, "sqlite", Map("sqlite" -> SqliteRenderer), "db/migrations/sqlite")
        .getOrElse(fail("expected migration context"))

    assertEquals(migration.migrationsDirectory, "db/migrations/sqlite")
    assertEquals(migration.migrations.length, 1)

    val initial = migration.migrations.head
    assertEquals(initial.version, "v1")
    assertEquals(initial.versionNumber, 1)
    assertEquals(initial.fileName, "v1_initial_schema.sql")
    assert(initial.sql.contains("CREATE TABLE bookmarks"))
    assertEquals(initial.schemaHash, sha256Hex(initial.sql))
    assert(migration.stateTableDdl.contains("_smithplates_migrations"))
  }

  test("returns None when schema has no tables") {
    assertEquals(
      SqlCodegenMigrationBuilder.build(
        SqlSchema(tables = Nil),
        "sqlite",
        Map("sqlite" -> SqliteRenderer),
        "db/migrations/sqlite"
      ),
      None
    )
  }
}
