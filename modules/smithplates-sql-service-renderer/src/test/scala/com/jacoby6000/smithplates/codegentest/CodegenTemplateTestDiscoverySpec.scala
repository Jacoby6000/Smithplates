package com.jacoby6000.smithplates.codegentest

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CodegenTemplateTestDiscoverySpec extends FunSuite {
  private val sqliteVariant = CodegenTemplateVariant("python", "db", "sqlite")

  private lazy val repoRoot: Path =
    Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize

  test("CodegenTemplateTestDiscovery - discovers language template fixture cases") {
    val cases =
      CodegenTemplateTestDiscovery.discover(repoRoot, "python", Set(sqliteVariant))

    assertEquals(cases.size, 12)
    assert(cases.forall(_.expectedOutputsByVariant.get(sqliteVariant).exists(_.nonEmpty)))
  }

  test("CodegenTemplateTestDiscovery - ignores empty and non-golden junk files under expected/") {
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

  test("CodegenTemplateTestDiscovery - unsupported.md suppresses expected files for a variant") {
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

  test("CodegenTemplateTestDiscovery - scopes dialect migration files per variant") {
    val tempRoot = Files.createTempDirectory("language-template-tests")
    try {
      val testsRoot       = tempRoot.resolve("templates/python/tests")
      val caseDirectory   = testsRoot.resolve("sample-case")
      val smithyDirectory = caseDirectory.resolve("smithy")
      val expectedRoot    = caseDirectory.resolve("expected")
      val sqliteDirectory = expectedRoot.resolve("src/db/sqlite")
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
      Files.writeString(expectedRoot.resolve("db/sqlite.sql"), "-- sqlite ddl\n")
      Files.writeString(expectedRoot.resolve("db/postgres.sql"), "-- postgres ddl\n")

      val postgresVariant = CodegenTemplateVariant("python", "db", "postgres")
      val testCase        =
        CodegenTemplateTestDiscovery
          .discover(tempRoot, "python", Set(sqliteVariant, postgresVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(sqliteVariant, Nil).map(_.relativePath).sorted,
        List("db/sqlite.sql", "src/db/sqlite/sample_repository_aiosqlite.py")
      )
      assertEquals(
        testCase.expectedOutputsByVariant.getOrElse(postgresVariant, Nil).map(_.relativePath),
        List("db/postgres.sql")
      )
    } finally deleteRecursively(tempRoot)
  }

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      Files.walk(path).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(Files.delete(_))
    }
}
