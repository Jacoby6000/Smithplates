package com.jacoby6000.smithy.stache.codegentest

import munit.FunSuite

import java.nio.file.Files

class CodegenTemplateTestDiscoverySpec extends FunSuite {
  private val sqliteVariant = CodegenTemplateVariant("python", "db", "sqlite")

  test("CodegenTemplateTestDiscovery - discovers language template fixture cases") {
    val cases =
      CodegenTemplateTestDiscovery.discover(getClass.getClassLoader, Set(sqliteVariant))

    assertEquals(cases.map(_.name), List("sql-derived-crud-auto-managed-columns", "sql-json-structs-containing-unions"))
    assert(cases.forall(_.expectedOutputsByVariant.get(sqliteVariant).exists(_.nonEmpty)))
  }

  test("CodegenTemplateTestDiscovery - unsupported.md suppresses expected files for a variant") {
    val tempRoot = Files.createTempDirectory("language-template-expected-outputs")
    try {
      val caseDirectory    = tempRoot.resolve("sample-case")
      val smithyDirectory  = caseDirectory.resolve("smithy")
      val variantDirectory = caseDirectory.resolve("src/db/sqlite")
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

      val resourceRoot = Files.createDirectories(tempRoot.resolve("python/expected-outputs"))
      Files.move(caseDirectory, resourceRoot.resolve("sample-case"))
      val classLoader  = new DirectoryClassLoader(tempRoot, getClass.getClassLoader)
      val testCase     =
        CodegenTemplateTestDiscovery
          .discover(classLoader, Set(sqliteVariant))
          .headOption
          .getOrElse(fail("expected discovered test case"))

      assertEquals(testCase.name, "sample-case")
      assertEquals(testCase.expectedOutputsByVariant.getOrElse(sqliteVariant, Nil), Nil)
      assert(
        CodegenTemplateTestDiscovery.isVariantUnsupported(
          testCase.resourceBasePath,
          sqliteVariant,
          classLoader
        )
      )
    } finally deleteRecursively(tempRoot)
  }

  private def deleteRecursively(path: java.nio.file.Path): Unit =
    if (Files.exists(path)) {
      Files.walk(path).sorted(java.util.Comparator.reverseOrder[java.nio.file.Path]()).forEach(Files.delete)
    }
}

final private class DirectoryClassLoader(root: java.nio.file.Path, parent: ClassLoader) extends ClassLoader(parent) {
  override def getResource(name: String): java.net.URL = {
    val path = root.resolve(name)
    if (Files.exists(path)) {
      path.toUri.toURL
    } else {
      null
    }
  }

  override def getResourceAsStream(name: String): java.io.InputStream = {
    val path = root.resolve(name)
    if (Files.isRegularFile(path)) {
      Files.newInputStream(path)
    } else {
      null
    }
  }
}
