package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.OutputId
import com.jacoby6000.smithplates.codegen.core.planning.SmithyBinding

/** Bundled `@sqlService` artifact paths relative to the DB template root. */
object SqlServiceCodegenDbArtifacts {
  val shared: List[CodegenOutput] =
    List(
      template(
        id = "python.sql.db.models",
        kind = ArtifactKind.Src,
        templatePath = "models/models.ssp",
        outputPath = "{{smithyNamespaceDir}}/models/{{serviceModuleName}}_models.py"
      ),
      template(
        id = "python.sql.db.service_protocol",
        kind = ArtifactKind.Src,
        templatePath = "service_protocol.ssp",
        outputPath = "{{smithyNamespaceDir}}/{{serviceModuleName}}_protocol.py"
      )
    )

  def sqlite(
      serviceTemplate: String = "sqlite/service_aiosqlite.ssp",
      serviceOutputFile: String = "sqlite/{{serviceFileName}}_aiosqlite.py",
      transactionRunResource: String = "sqlite/sqlite_transaction_run.py",
      transactionRunOutputFile: String = "sqlite/sqlite_transaction_run.py",
      migrationsServiceTemplate: String = "sqlite/migrations_service.ssp",
      migrationsServiceOutputFile: String = "{{smithyNamespaceDir}}/sqlite/sqlite_migrations.py",
      integrationTestTemplate: String = "sqlite/tests/service_derived_sql_integration_tests.ssp",
      integrationTestOutputFile: String = "{{smithyNamespaceDir}}/sqlite/test_{{serviceModuleName}}_derived_sql.py"
  ): List[CodegenOutput] =
    shared ++ List(
      bundled(
        id = "python.sql.db.sqlite.transaction_run",
        kind = ArtifactKind.Src,
        resourcePath = transactionRunResource,
        outputPath = "{{smithyNamespaceDir}}/" + transactionRunOutputFile
      ),
      template(
        id = "python.sql.db.sqlite.migrations_service",
        kind = ArtifactKind.Src,
        templatePath = migrationsServiceTemplate,
        outputPath = migrationsServiceOutputFile
      ),
      template(
        id = "python.sql.db.sqlite.service",
        kind = ArtifactKind.Src,
        templatePath = serviceTemplate,
        outputPath =
          "{{smithyNamespaceDir}}/" + serviceOutputFile.replace("{{serviceFileName}}", "{{serviceModuleName}}")
      ),
      template(
        id = "python.sql.db.sqlite.integration_tests",
        kind = ArtifactKind.Test,
        templatePath = integrationTestTemplate,
        outputPath = integrationTestOutputFile
      )
    )

  def postgres(
      serviceTemplate: String = "postgres/service_psycopg.ssp",
      serviceOutputFile: String = "postgres/{{serviceFileName}}_psycopg.py",
      transactionRunResource: String = "postgres/psycopg_transaction_run.py",
      transactionRunOutputFile: String = "postgres/psycopg_transaction_run.py",
      migrationsServiceTemplate: String = "postgres/migrations_service.ssp",
      migrationsServiceOutputFile: String = "{{smithyNamespaceDir}}/postgres/psycopg_migrations.py",
      integrationTestTemplate: String = "postgres/tests/service_derived_sql_integration_tests_postgres.ssp",
      integrationTestOutputFile: String = "{{smithyNamespaceDir}}/postgres/test_{{serviceModuleName}}_derived_sql.py",
      testcontainersStubResource: String = "postgres/stubs/testcontainers/postgres.pyi",
      testcontainersStubOutputFile: String = "{{smithyNamespaceDir}}/postgres/stubs/testcontainers/postgres.pyi"
  ): List[CodegenOutput] =
    shared ++ List(
      bundled(
        id = "python.sql.db.postgres.transaction_run",
        kind = ArtifactKind.Src,
        resourcePath = transactionRunResource,
        outputPath = "{{smithyNamespaceDir}}/" + transactionRunOutputFile
      ),
      template(
        id = "python.sql.db.postgres.migrations_service",
        kind = ArtifactKind.Src,
        templatePath = migrationsServiceTemplate,
        outputPath = migrationsServiceOutputFile
      ),
      template(
        id = "python.sql.db.postgres.service",
        kind = ArtifactKind.Src,
        templatePath = serviceTemplate,
        outputPath =
          "{{smithyNamespaceDir}}/" + serviceOutputFile.replace("{{serviceFileName}}", "{{serviceModuleName}}")
      ),
      template(
        id = "python.sql.db.postgres.integration_tests",
        kind = ArtifactKind.Test,
        templatePath = integrationTestTemplate,
        outputPath = integrationTestOutputFile
      ),
      bundled(
        id = "python.sql.db.postgres.testcontainers_stub",
        kind = ArtifactKind.Test,
        resourcePath = testcontainersStubResource,
        outputPath = testcontainersStubOutputFile
      )
    )

  def templatePath(output: CodegenOutput): Option[String] =
    output match {
      case template: CodegenOutput.CodegenTemplateBindingOutput => Some(template.templatePath)
      case _: CodegenOutput.CodegenStaticOutput                 => None
    }

  def dialectSpecific(dialectKey: String): List[CodegenOutput] =
    dialectKey match {
      case "sqlite"   => sqlite().drop(shared.length)
      case "postgres" => postgres().drop(shared.length)
      case other      => throw new IllegalArgumentException(s"unsupported dialect key: $other")
    }

  def forEnabledDialects(dialectKeys: List[String]): List[CodegenOutput] =
    if (dialectKeys.isEmpty) {
      shared
    } else {
      shared ++ dialectKeys.flatMap(dialectSpecific)
    }

  def bundledTemplatePaths: Set[String] =
    Set(
      "sqlite/sqlite_transaction_run.py",
      "postgres/psycopg_transaction_run.py",
      "postgres/stubs/testcontainers/postgres.pyi"
    )

  def template(
      id: String,
      kind: ArtifactKind,
      templatePath: String,
      outputPath: String
  ): CodegenOutput.CodegenTemplateBindingOutput =
    CodegenOutput.CodegenTemplateBindingOutput(
      id = OutputId(id),
      kind = kind,
      templatePath = templatePath,
      outputPath = outputPath,
      binding = SmithyBinding.Service
    )

  def bundled(
      id: String,
      kind: ArtifactKind,
      resourcePath: String,
      outputPath: String
  ): CodegenOutput.CodegenTemplateBindingOutput =
    template(
      id = id,
      kind = kind,
      templatePath = resourcePath,
      outputPath = outputPath
    )
}
