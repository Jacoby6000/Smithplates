package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.sql.{PostgresDialect, SqlDialect, SqliteDialect}

/** Bundled `@sqlService` artifact paths under the `db/` service-type layout. */
object SqlServiceCodegenDbArtifacts {
  val shared: List[SqlServiceCodegenArtifactConfig] =
    List(
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = "db/model/models.mustache",
        outputFile = "db/model/{{serviceFileName}}_models.py"
      ),
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = "db/service_protocol.mustache",
        outputFile = "db/{{serviceFileName}}_protocol.py"
      )
    )

  def sqlite(
      serviceTemplate: String = "db/sqlite/service_aiosqlite.mustache",
      serviceOutputFile: String = "db/sqlite/{{serviceFileName}}_aiosqlite.py",
      integrationTestTemplate: String = "db/sqlite/tests/service_derived_sql_integration_tests.mustache",
      integrationTestOutputFile: String = "db/sqlite/test_{{serviceFileName}}_derived_sql.py"
  ): List[SqlServiceCodegenArtifactConfig] =
    shared ++ List(
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = serviceTemplate,
        outputFile = serviceOutputFile
      ),
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Test,
        template = integrationTestTemplate,
        outputFile = integrationTestOutputFile
      )
    )

  def postgres(
      serviceTemplate: String = "db/postgres/service_psycopg.mustache",
      serviceOutputFile: String = "db/postgres/{{serviceFileName}}_psycopg.py",
      integrationTestTemplate: String = "db/postgres/tests/service_derived_sql_integration_tests_postgres.mustache",
      integrationTestOutputFile: String = "db/postgres/test_{{serviceFileName}}_derived_sql.py"
  ): List[SqlServiceCodegenArtifactConfig] =
    shared ++ List(
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = serviceTemplate,
        outputFile = serviceOutputFile
      ),
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Test,
        template = integrationTestTemplate,
        outputFile = integrationTestOutputFile
      )
    )

  def isIntegrationTestTemplate(template: String): Boolean =
    template.contains("service_derived_sql_integration_tests")

  def dialectSpecific(dialect: SqlDialect): List[SqlServiceCodegenArtifactConfig] =
    dialect match {
      case SqliteDialect   => sqlite().drop(shared.length)
      case PostgresDialect => postgres().drop(shared.length)
    }

  def forEnabledDialects(dialects: List[SqlDialect]): List[SqlServiceCodegenArtifactConfig] =
    if (dialects.isEmpty) {
      shared
    } else {
      shared ++ dialects.flatMap(dialectSpecific)
    }
}
