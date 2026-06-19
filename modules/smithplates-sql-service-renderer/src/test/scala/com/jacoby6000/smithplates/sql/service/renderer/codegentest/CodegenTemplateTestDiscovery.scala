package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters.*

object CodegenTemplateTestDiscovery {
  val TestsDirectoryName: String    = "tests"
  val ExpectedDirectoryName: String = "expected"
  val SmithyDirectoryName: String   = "smithy"
  val SmithyBuildFileName: String   = "smithy-build.json"
  val SmithyFileName: String        = "smithy-files.smithy"
  val UnsupportedFileName: String   = "unsupported.md"

  def discover(
      repoRoot: Path,
      languageId: String,
      variants: Set[CodegenTemplateVariant]
  ): List[CodegenTemplateTestCase] = {
    val testsRoot = repoRoot.resolve("templates").resolve(languageId).resolve(TestsDirectoryName)
    if (!Files.isDirectory(testsRoot)) {
      return Nil
    }

    Files
      .list(testsRoot)
      .iterator()
      .asScala
      .filter(Files.isDirectory(_))
      .map(_.getFileName.toString)
      .filterNot(_ == ExpectedDirectoryName)
      .toList
      .sorted
      .map(testName => loadTestCase(testsRoot.resolve(testName), testName, variants))
  }

  def isVariantUnsupported(
      testCase: CodegenTemplateTestCase,
      variant: CodegenTemplateVariant
  ): Boolean = {
    val namespacePathPrefix = SmithyNamespaceTestSupport.namespacePathPrefix(testCase.smithyNamespace)
    Files.isRegularFile(testCase.caseDirectory.resolve(variant.unsupportedFilePath(namespacePathPrefix)))
  }

  def warnMissingVariantExpectations(
      testCase: CodegenTemplateTestCase,
      variant: CodegenTemplateVariant
  ): Unit = {
    if (isVariantUnsupported(testCase, variant)) {
      return
    }

    val hasExpectedOutputs = testCase.expectedOutputsByVariant.get(variant).exists(_.nonEmpty)
    if (!hasExpectedOutputs) {
      val namespacePathPrefix = SmithyNamespaceTestSupport.namespacePathPrefix(testCase.smithyNamespace)
      Console.err.println(
        s"WARNING: Test case '${testCase.name}' has no expected output data for variant ${variant.resourcePath(namespacePathPrefix)} under ${testCase.caseDirectory}"
      )
    }
  }

  private def loadTestCase(
      caseDirectory: Path,
      testName: String,
      variants: Set[CodegenTemplateVariant]
  ): CodegenTemplateTestCase = {
    val smithyPath      = caseDirectory.resolve(SmithyDirectoryName).resolve(SmithyFileName)
    val smithyContent   = Files.readString(smithyPath, StandardCharsets.UTF_8)
    val smithyNamespace = SmithyNamespaceTestSupport.parseSmithyNamespace(smithyContent)

    val expectedOutputsByVariant =
      variants.toList.sorted.map { variant =>
        variant -> listExpectedFiles(caseDirectory, variant, smithyNamespace)
      }.toMap

    CodegenTemplateTestCase(
      name = testName,
      caseDirectory = caseDirectory,
      smithyModelId = s"$testName/$SmithyDirectoryName/$SmithyFileName",
      smithyContent = smithyContent,
      smithyNamespace = smithyNamespace,
      expectedOutputsByVariant = expectedOutputsByVariant
    )
  }

  private def listExpectedFiles(
      caseDirectory: Path,
      variant: CodegenTemplateVariant,
      smithyNamespace: String
  ): List[CodegenTemplateExpectedFile] = {
    val testCase =
      CodegenTemplateTestCase(
        name = caseDirectory.getFileName.toString,
        caseDirectory = caseDirectory,
        smithyModelId = "",
        smithyContent = "",
        smithyNamespace = smithyNamespace,
        expectedOutputsByVariant = Map.empty
      )
    if (isVariantUnsupported(testCase, variant)) {
      return Nil
    }

    val namespacePathPrefix          = SmithyNamespaceTestSupport.namespacePathPrefix(smithyNamespace)
    val expectedRoot                 = caseDirectory.resolve(ExpectedDirectoryName)
    val namespaceRootPath            = expectedRoot.resolve(variant.namespaceResourcePath(namespacePathPrefix))
    val implementationRootPath       = expectedRoot.resolve(variant.resourcePath(namespacePathPrefix))
    val implementationTestOutputPath = expectedRoot.resolve(variant.testOutputResourcePath(namespacePathPrefix))

    val serviceTypeFiles        =
      variant.goldenTestLayout match {
        case GoldenTestLayout.HttpNested =>
          listFilesRelativeToExpectedRoot(expectedRoot, namespaceRootPath)
        case GoldenTestLayout.SqlDialect =>
          val sharedModelFiles       =
            variant.sharedModelsResourcePath(namespacePathPrefix).toList.flatMap { modelsPath =>
              listFilesRelativeToExpectedRoot(expectedRoot, expectedRoot.resolve(modelsPath))
            }
          val sharedServiceTypeFiles =
            listFilesDirectlyInDirectoryRelativeToExpectedRoot(
              expectedRoot,
              namespaceRootPath
            ).filter { expected =>
              val fileName = expected.relativePath.split('/').last
              fileName.endsWith(".py")
            }
          sharedModelFiles ++ sharedServiceTypeFiles
      }
    val implementationFiles     =
      variant.goldenTestLayout match {
        case GoldenTestLayout.SqlDialect =>
          listFilesDirectlyInDirectoryRelativeToExpectedRoot(
            expectedRoot,
            implementationRootPath
          )
        case _                           =>
          Nil
      }
    val implementationTestFiles =
      listFilesRelativeToExpectedRoot(
        expectedRoot,
        implementationTestOutputPath
      )
    val migrationFiles          =
      variant.goldenTestLayout match {
        case GoldenTestLayout.SqlDialect =>
          listDialectMigrationFiles(
            caseDirectory,
            expectedRoot,
            variant.implementationId
          )
        case _                           =>
          Nil
      }

    (serviceTypeFiles ++ implementationFiles ++ implementationTestFiles ++ migrationFiles)
      .sortBy(_.relativePath)
  }

  private val GoldenFileSuffixes: Set[String] = Set(".py", ".sql", ".pyi")

  private def isGoldenExpectedFile(path: Path): Boolean = {
    val fileName = path.getFileName.toString
    if (fileName == UnsupportedFileName) {
      false
    } else if (!Files.isRegularFile(path) || Files.size(path) == 0L) {
      false
    } else {
      GoldenFileSuffixes.exists(suffix => fileName.endsWith(suffix))
    }
  }

  private def listDialectMigrationFiles(
      caseDirectory: Path,
      expectedRoot: Path,
      implementationId: String
  ): List[CodegenTemplateExpectedFile] = {
    val migrationDirectory =
      resolveMigrationDirectory(caseDirectory, implementationId)
    val migrationDir       = expectedRoot.resolve(migrationDirectory.stripPrefix("/"))
    listFilesRelativeToExpectedRoot(expectedRoot, migrationDir)
  }

  private def resolveMigrationDirectory(caseDirectory: Path, dialectKey: String): String = {
    val configPath = caseDirectory.resolve(SmithyBuildFileName)
    if (!Files.isRegularFile(configPath)) {
      return s"db/migrations/$dialectKey"
    }
    val content    = Files.readString(configPath, StandardCharsets.UTF_8)
    val pattern    =
      s""""$dialectKey"\\s*:\\s*\\{[^}]*"migrationLocation"\\s*:\\s*"([^"]+)"""".r
    pattern
      .findFirstMatchIn(content)
      .map(_.group(1))
      .getOrElse(s"db/migrations/$dialectKey")
  }

  private def listFilesRelativeToExpectedRoot(
      expectedRoot: Path,
      directoryPath: Path
  ): List[CodegenTemplateExpectedFile] =
    if (!Files.isDirectory(directoryPath)) {
      Nil
    } else {
      Files
        .walk(directoryPath)
        .iterator()
        .asScala
        .filter(isGoldenExpectedFile)
        .map { path =>
          val relativePath =
            expectedRoot.relativize(path).toString.replace('\\', '/')
          CodegenTemplateExpectedFile(
            relativePath = relativePath,
            content = Files.readString(path, StandardCharsets.UTF_8)
          )
        }
        .toList
    }

  private def listFilesDirectlyInDirectoryRelativeToExpectedRoot(
      expectedRoot: Path,
      directoryPath: Path
  ): List[CodegenTemplateExpectedFile] =
    if (!Files.isDirectory(directoryPath)) {
      Nil
    } else {
      Files
        .list(directoryPath)
        .iterator()
        .asScala
        .filter(isGoldenExpectedFile)
        .map { path =>
          val relativePath =
            expectedRoot.relativize(path).toString.replace('\\', '/')
          CodegenTemplateExpectedFile(
            relativePath = relativePath,
            content = Files.readString(path, StandardCharsets.UTF_8)
          )
        }
        .toList
    }
}
