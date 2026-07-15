package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CodegenTemplateTestDiscoverySpec extends FunSuite {
  test("discovers language template fixture cases") {
    val cases =
      CodegenTemplateTestDiscovery.discover(
        CodegenTemplateTestDiscoverySpec.internal.repoRoot,
        "python",
        Set(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant))

    assertEquals(cases.size, 38)
    val sqlCases = cases.filterNot(_.name.startsWith("http-"))
    assertEquals(sqlCases.size, 21)
    assert(
      sqlCases.forall(
        _.expectedOutputsByVariant.get(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant).exists(_.nonEmpty)))
  }

  test("ignores empty and non-golden junk files under expected/") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot        = tempRoot.resolve("templates/python/tests")
      val caseDirectory    = testsRoot.resolve("sample-case")
      val smithyDirectory  = caseDirectory.resolve("smithy")
      val expectedRoot     = caseDirectory.resolve("expected")
      val variantDirectory = expectedRoot.resolve("src/generated/example/sqlite")
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
          .discover(tempRoot, "python", Set(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant
          .getOrElse(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant, Nil)
          .map(_.relativePath),
        List("src/generated/example/sqlite/sample_repository_aiosqlite.py")
      )
    } finally CodegenTemplateTestDiscoverySpec.internal.deleteRecursively(tempRoot)
  }

  test("unsupported.md suppresses expected files for a variant") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot        = tempRoot.resolve("templates/python/tests")
      val caseDirectory    = testsRoot.resolve("sample-case")
      val smithyDirectory  = caseDirectory.resolve("smithy")
      val expectedRoot     = caseDirectory.resolve("expected")
      val variantDirectory = expectedRoot.resolve("src/generated/example/sqlite")
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
          .discover(tempRoot, "python", Set(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(testCase.name, "sample-case")
      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant, Nil),
        Nil)
      assert(
        CodegenTemplateTestDiscovery
          .isVariantUnsupported(testCase, CodegenTemplateTestDiscoverySpec.internal.sqliteVariant))
    } finally CodegenTemplateTestDiscoverySpec.internal.deleteRecursively(tempRoot)
  }

  test("scopes dialect migration files per variant") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot             = tempRoot.resolve("templates/python/tests")
      val caseDirectory         = testsRoot.resolve("sample-case")
      val smithyDirectory       = caseDirectory.resolve("smithy")
      val expectedRoot          = caseDirectory.resolve("expected")
      val sqliteDirectory       = expectedRoot.resolve("src/generated/example/sqlite")
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
          .discover(tempRoot, "python", Set(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant, postgresVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant
          .getOrElse(CodegenTemplateTestDiscoverySpec.internal.sqliteVariant, Nil)
          .map(_.relativePath)
          .sorted,
        List(
          "db/migrations/sqlite/v1_initial_schema.sql",
          "src/generated/example/sqlite/sample_repository_aiosqlite.py")
      )
      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(postgresVariant, Nil).map(_.relativePath),
        List("db/migrations/postgres/v1_initial_schema.sql")
      )
    } finally CodegenTemplateTestDiscoverySpec.internal.deleteRecursively(tempRoot)
  }

  test("CodegenTemplateTestDiscovery - discovers nested http server files for http variants") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot       = tempRoot.resolve("templates/python/tests")
      val caseDirectory   = testsRoot.resolve("sample-http-case")
      val smithyDirectory = caseDirectory.resolve("smithy")
      val namespaceRoot   = caseDirectory.resolve("expected/src/generated/example")
      val apisDirectory   = namespaceRoot.resolve("apis")
      Files.createDirectories(smithyDirectory)
      Files.createDirectories(apisDirectory)
      Files.writeString(
        smithyDirectory.resolve("smithy-files.smithy"),
        """$version: "2.0"
          |namespace example
          |structure Placeholder {}
          |""".stripMargin
      )
      Files.writeString(namespaceRoot.resolve("app_factory.py"), "# app factory\n")
      Files.writeString(apisDirectory.resolve("v1_widgets_api.py"), "# routes\n")
      Files.writeString(namespaceRoot.resolve("widget_output.py"), "# model\n")

      val serverVariant = CodegenTemplateVariant("python", "http", "server")
      val testCase      =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(serverVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(serverVariant, Nil).map(_.relativePath).sorted,
        List(
          "src/generated/example/apis/v1_widgets_api.py",
          "src/generated/example/app_factory.py",
          "src/generated/example/widget_output.py"
        )
      )
    } finally CodegenTemplateTestDiscoverySpec.internal.deleteRecursively(tempRoot)
  }

  test("CodegenTemplateTestDiscovery - discovers nested http client files for http variants") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot        = tempRoot.resolve("templates/python/tests")
      val caseDirectory    = testsRoot.resolve("sample-http-client-case")
      val smithyDirectory  = caseDirectory.resolve("smithy")
      val namespaceRoot    = caseDirectory.resolve("expected/src/generated/example")
      val clientDirectory  = namespaceRoot.resolve("client")
      val clientsDirectory = namespaceRoot.resolve("clients")
      Files.createDirectories(smithyDirectory)
      Files.createDirectories(clientDirectory)
      Files.createDirectories(clientsDirectory)
      Files.writeString(
        smithyDirectory.resolve("smithy-files.smithy"),
        """$version: "2.0"
          |namespace example
          |structure Placeholder {}
          |""".stripMargin
      )
      Files.writeString(clientDirectory.resolve("client_registry.py"), "# registry\n")
      Files.writeString(clientsDirectory.resolve("warehouse_client.py"), "# client\n")
      Files.writeString(namespaceRoot.resolve("widget_output.py"), "# model\n")

      val clientVariant = CodegenTemplateVariant("python", "http", "client")
      val testCase      =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(clientVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(clientVariant, Nil).map(_.relativePath).sorted,
        List(
          "src/generated/example/client/client_registry.py",
          "src/generated/example/clients/warehouse_client.py",
          "src/generated/example/widget_output.py"
        )
      )
    } finally CodegenTemplateTestDiscoverySpec.internal.deleteRecursively(tempRoot)
  }
}
object CodegenTemplateTestDiscoverySpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val sqliteVariant                       = CodegenTemplateVariant("python", "db", "sqlite")
    lazy val repoRoot: Path                 =
      Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize
    def deleteRecursively(path: Path): Unit =
      if (Files.exists(path)) {
        Files.walk(path).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(Files.delete(_))
      }
  }
}
