package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CodegenTemplateTestDiscoverySpec extends FunSuite {
  private val sqliteVariant = CodegenTemplateVariant("python", "db", "sqlite")

  private lazy val repoRoot: Path =
    Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize

  test("discovers language template fixture cases") {
    val cases =
      CodegenTemplateTestDiscovery.discover(repoRoot, "python", Set(sqliteVariant))

    assertEquals(cases.size, 25)
    val sqlCases = cases.filterNot(_.name.startsWith("http-"))
    assertEquals(sqlCases.size, 15)
    assert(sqlCases.forall(_.expectedOutputsByVariant.get(sqliteVariant).exists(_.nonEmpty)))
  }

  test("ignores empty and non-golden junk files under expected/") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot        = tempRoot.resolve("templates/python/tests")
      val caseDirectory    = testsRoot.resolve("sample-case")
      val smithyDirectory  = caseDirectory.resolve("smithy")
      val expectedRoot     = caseDirectory.resolve("expected")
      val variantDirectory = expectedRoot.resolve("src/db/sqlite")
      Files.createDirectories(smithyDirectory)
      Files.createDirectories(variantDirectory)
      Files.writeString(
        smithyDirectory.resolve("smithy-files.smithy"),
        """$version: "2.0"
          |namespace example
          |structure Placeholder {}
          |""".stripMargin
      )
      Files.writeString(
        variantDirectory.resolve("sample_repository_aiosqlite.py"),
        "# valid golden file\n"
      )
      Files.writeString(variantDirectory.resolve("sample_repository_aiosql"), "")
      Files.writeString(variantDirectory.resolve("junk-fragment"), "partial write")

      val testCase =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(sqliteVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(sqliteVariant, Nil).map(_.relativePath),
        List("src/db/sqlite/sample_repository_aiosqlite.py")
      )
    } finally deleteRecursively(tempRoot)
  }

  test("unsupported.md suppresses expected files for a variant") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot        = tempRoot.resolve("templates/python/tests")
      val caseDirectory    = testsRoot.resolve("sample-case")
      val smithyDirectory  = caseDirectory.resolve("smithy")
      val expectedRoot     = caseDirectory.resolve("expected")
      val variantDirectory = expectedRoot.resolve("src/db/sqlite")
      Files.createDirectories(smithyDirectory)
      Files.createDirectories(variantDirectory)
      Files.writeString(
        smithyDirectory.resolve("smithy-files.smithy"),
        """$version: "2.0"
          |namespace example
          |structure Placeholder {}
          |""".stripMargin
      )
      Files.writeString(
        variantDirectory.resolve("unsupported.md"),
        "Python backend does not implement this scenario yet."
      )

      val testCase =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(sqliteVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(testCase.name, "sample-case")
      assertEquals(testCase.expectedOutputsByVariant.getOrElse(sqliteVariant, Nil), Nil)
      assert(CodegenTemplateTestDiscovery.isVariantUnsupported(testCase, sqliteVariant))
    } finally deleteRecursively(tempRoot)
  }

  test("scopes dialect migration files per variant") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot             = tempRoot.resolve("templates/python/tests")
      val caseDirectory         = testsRoot.resolve("sample-case")
      val smithyDirectory       = caseDirectory.resolve("smithy")
      val expectedRoot          = caseDirectory.resolve("expected")
      val sqliteDirectory       = expectedRoot.resolve("src/db/sqlite")
      Files.createDirectories(smithyDirectory)
      Files.createDirectories(sqliteDirectory)
      Files.createDirectories(expectedRoot.resolve("db"))
      Files.writeString(
        smithyDirectory.resolve("smithy-files.smithy"),
        """$version: "2.0"
          |namespace example
          |structure Placeholder {}
          |""".stripMargin
      )
      Files.writeString(
        sqliteDirectory.resolve("sample_repository_aiosqlite.py"),
        "# valid golden file\n"
      )
      val sqliteMigrationsDir   = expectedRoot.resolve("db/migrations/sqlite")
      val postgresMigrationsDir = expectedRoot.resolve("db/migrations/postgres")
      Files.createDirectories(sqliteMigrationsDir)
      Files.createDirectories(postgresMigrationsDir)
      Files.writeString(
        caseDirectory.resolve("smithy-build.json"),
        """{
          |  "plugins": {
          |    "smithplates": {
          |      "sql": {
          |        "sqlite": { "enable": true, "migrationLocation": "db/migrations/sqlite" },
          |        "postgres": { "enable": true, "migrationLocation": "db/migrations/postgres" }
          |      }
          |    }
          |  }
          |}""".stripMargin
      )
      Files.writeString(sqliteMigrationsDir.resolve("v1_initial_schema.sql"), "-- sqlite ddl\n")
      Files.writeString(postgresMigrationsDir.resolve("v1_initial_schema.sql"), "-- postgres ddl\n")

      val postgresVariant = CodegenTemplateVariant("python", "db", "postgres")
      val testCase        =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(sqliteVariant, postgresVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(sqliteVariant, Nil).map(_.relativePath).sorted,
        List("db/migrations/sqlite/v1_initial_schema.sql", "src/db/sqlite/sample_repository_aiosqlite.py")
      )
      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(postgresVariant, Nil).map(_.relativePath),
        List("db/migrations/postgres/v1_initial_schema.sql")
      )
    } finally deleteRecursively(tempRoot)
  }

  test("CodegenTemplateTestDiscovery - discovers nested http server files for http variants") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot       = tempRoot.resolve("templates/python/tests")
      val caseDirectory   = testsRoot.resolve("sample-http-case")
      val smithyDirectory = caseDirectory.resolve("smithy")
      val serverDirectory = caseDirectory.resolve("expected/src/http/server")
      val apisDirectory   = serverDirectory.resolve("apis")
      val modelsDirectory = caseDirectory.resolve("expected/src/http/models")
      Files.createDirectories(smithyDirectory)
      Files.createDirectories(apisDirectory)
      Files.createDirectories(modelsDirectory)
      Files.writeString(
        smithyDirectory.resolve("smithy-files.smithy"),
        """$version: "2.0"
          |namespace example
          |structure Placeholder {}
          |""".stripMargin
      )
      Files.writeString(serverDirectory.resolve("app_factory.py"), "# app factory\n")
      Files.writeString(apisDirectory.resolve("v1_widgets_api.py"), "# routes\n")
      Files.writeString(modelsDirectory.resolve("widget_output.py"), "# model\n")

      val serverVariant = CodegenTemplateVariant("python", "http", "server")
      val testCase      =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(serverVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(serverVariant, Nil).map(_.relativePath).sorted,
        List(
          "src/http/models/widget_output.py",
          "src/http/server/apis/v1_widgets_api.py",
          "src/http/server/app_factory.py"
        )
      )
    } finally deleteRecursively(tempRoot)
  }

  test("CodegenTemplateTestDiscovery - discovers nested http client files for http variants") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot        = tempRoot.resolve("templates/python/tests")
      val caseDirectory    = testsRoot.resolve("sample-http-client-case")
      val smithyDirectory  = caseDirectory.resolve("smithy")
      val clientDirectory  = caseDirectory.resolve("expected/src/http/client")
      val clientsDirectory = clientDirectory.resolve("clients")
      val modelsDirectory  = caseDirectory.resolve("expected/src/http/models")
      Files.createDirectories(smithyDirectory)
      Files.createDirectories(clientsDirectory)
      Files.createDirectories(modelsDirectory)
      Files.writeString(
        smithyDirectory.resolve("smithy-files.smithy"),
        """$version: "2.0"
          |namespace example
          |structure Placeholder {}
          |""".stripMargin
      )
      Files.writeString(clientDirectory.resolve("client_registry.py"), "# registry\n")
      Files.writeString(clientsDirectory.resolve("warehouse_client.py"), "# client\n")
      Files.writeString(modelsDirectory.resolve("widget_output.py"), "# model\n")

      val clientVariant = CodegenTemplateVariant("python", "http", "client")
      val testCase      =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(clientVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(clientVariant, Nil).map(_.relativePath).sorted,
        List(
          "src/http/client/client_registry.py",
          "src/http/client/clients/warehouse_client.py",
          "src/http/models/widget_output.py"
        )
      )
    } finally deleteRecursively(tempRoot)
  }

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      Files.walk(path).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(Files.delete(_))
    }
}
