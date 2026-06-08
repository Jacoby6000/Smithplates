package com.jacoby6000.smithy.stache.sql.codegen

/** Bundled `@sqlService` artifact paths under the `db/` service-type layout. */
object SqlServiceCodegenDbArtifacts {
  val shared: List[SqlServiceCodegenArtifactConfig] =
    List(
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = "model/models.ssp",
        outputFile = "db/model/{{serviceFileName}}_models.py"
      ),
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = "service_protocol.ssp",
        outputFile = "db/{{serviceFileName}}_protocol.py"
      )
    )

  def sqlite(
      serviceTemplate: String = "sqlite/service_aiosqlite.ssp",
      serviceOutputFile: String = "db/sqlite/{{serviceFileName}}_aiosqlite.py",
      transactionRunResource: String = "sqlite/sqlite_transaction_run.py",
      transactionRunOutputFile: String = "db/sqlite/sqlite_transaction_run.py",
      integrationTestTemplate: String = "sqlite/tests/service_derived_sql_integration_tests.ssp",
      integrationTestOutputFile: String = "db/sqlite/test_{{serviceFileName}}_derived_sql.py"
  ): List[SqlServiceCodegenArtifactConfig] =
    shared ++ List(
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = transactionRunResource,
        outputFile = transactionRunOutputFile,
        bundledResource = true
      ),
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
      serviceTemplate: String = "postgres/service_psycopg.ssp",
      serviceOutputFile: String = "db/postgres/{{serviceFileName}}_psycopg.py",
      transactionRunResource: String = "postgres/psycopg_transaction_run.py",
      transactionRunOutputFile: String = "db/postgres/psycopg_transaction_run.py",
      integrationTestTemplate: String = "postgres/tests/service_derived_sql_integration_tests_postgres.ssp",
      integrationTestOutputFile: String = "db/postgres/test_{{serviceFileName}}_derived_sql.py",
      testcontainersStubResource: String = "postgres/stubs/testcontainers/postgres.pyi",
      testcontainersStubOutputFile: String = "db/postgres/stubs/testcontainers/postgres.pyi"
  ): List[SqlServiceCodegenArtifactConfig] =
    shared ++ List(
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = transactionRunResource,
        outputFile = transactionRunOutputFile,
        bundledResource = true
      ),
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Src,
        template = serviceTemplate,
        outputFile = serviceOutputFile
      ),
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Test,
        template = integrationTestTemplate,
        outputFile = integrationTestOutputFile
      ),
      SqlServiceCodegenArtifactConfig(
        kind = SqlServiceCodegenArtifactKind.Test,
        template = testcontainersStubResource,
        outputFile = testcontainersStubOutputFile,
        bundledResource = true
      )
    )

  def isIntegrationTestTemplate(template: String): Boolean =
    template.contains("service_derived_sql_integration_tests")

  def isPostgresTestcontainersStub(template: String): Boolean =
    template.contains("stubs/testcontainers")

  def dialectSpecific(dialectKey: String): List[SqlServiceCodegenArtifactConfig] =
    dialectKey match {
      case "sqlite"   => sqlite().drop(shared.length)
      case "postgres" => postgres().drop(shared.length)
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def forEnabledDialects(dialectKeys: List[String]): List[SqlServiceCodegenArtifactConfig] =
    if (dialectKeys.isEmpty) {
      shared
    } else {
      shared ++ dialectKeys.flatMap(dialectSpecific)
    }
}
