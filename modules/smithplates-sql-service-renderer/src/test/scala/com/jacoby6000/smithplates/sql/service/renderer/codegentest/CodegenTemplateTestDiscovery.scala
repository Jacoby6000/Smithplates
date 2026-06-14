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
  ): Boolean =
    Files.isRegularFile(testCase.caseDirectory.resolve(variant.unsupportedFilePath))

  def warnMissingVariantExpectations(
      testCase: CodegenTemplateTestCase,
      variant: CodegenTemplateVariant
  ): Unit = {
    if (isVariantUnsupported(testCase, variant)) {
      return
    }

    val hasExpectedOutputs = testCase.expectedOutputsByVariant.get(variant).exists(_.nonEmpty)
    if (!hasExpectedOutputs) {
      Console.err.println(
        s"WARNING: Test case '${testCase.name}' has no expected output data for variant ${variant.resourcePath} under ${testCase.caseDirectory}"
      )
    }
  }

  private def loadTestCase(
      caseDirectory: Path,
      testName: String,
      variants: Set[CodegenTemplateVariant]
  ): CodegenTemplateTestCase = {
    val smithyPath    = caseDirectory.resolve(SmithyDirectoryName).resolve(SmithyFileName)
    val smithyContent = Files.readString(smithyPath, StandardCharsets.UTF_8)

    val expectedOutputsByVariant =
      variants.toList.sorted.map { variant =>
        variant -> listExpectedFiles(caseDirectory, variant)
      }.toMap

    CodegenTemplateTestCase(
      name = testName,
      caseDirectory = caseDirectory,
      smithyModelId = s"$testName/$SmithyDirectoryName/$SmithyFileName",
      smithyContent = smithyContent,
      expectedOutputsByVariant = expectedOutputsByVariant
    )
  }

  private def listExpectedFiles(
      caseDirectory: Path,
      variant: CodegenTemplateVariant
  ): List[CodegenTemplateExpectedFile] = {
    val testCase = CodegenTemplateTestCase(
      name = caseDirectory.getFileName.toString,
      caseDirectory = caseDirectory,
      smithyModelId = "",
      smithyContent = "",
      expectedOutputsByVariant = Map.empty
    )
    if (isVariantUnsupported(testCase, variant)) {
      return Nil
    }

    val expectedRoot                 = caseDirectory.resolve(ExpectedDirectoryName)
    val serviceTypeRootPath          = expectedRoot.resolve(variant.serviceTypeResourcePath)
    val implementationRootPath       = expectedRoot.resolve(variant.resourcePath)
    val implementationTestOutputPath = expectedRoot.resolve(variant.testOutputResourcePath)

    val serviceTypeFiles        =
      if (variant.serviceTypeId == "api") {
        listFilesRelativeToExpectedRoot(expectedRoot, serviceTypeRootPath)
      } else {
        val sharedModelFiles       =
          listFilesRelativeToExpectedRoot(
            expectedRoot,
            serviceTypeRootPath.resolve("model")
          )
        val sharedServiceTypeFiles =
          listFilesDirectlyInDirectoryRelativeToExpectedRoot(
            expectedRoot,
            serviceTypeRootPath
          )
        sharedModelFiles ++ sharedServiceTypeFiles
      }
    val implementationFiles     =
      if (variant.serviceTypeId == "api") {
        Nil
      } else {
        listFilesDirectlyInDirectoryRelativeToExpectedRoot(
          expectedRoot,
          implementationRootPath
        )
      }
    val implementationTestFiles =
      listFilesRelativeToExpectedRoot(
        expectedRoot,
        implementationTestOutputPath
      )
    val migrationFiles          =
      if (variant.serviceTypeId == "api") {
        Nil
      } else {
        listDialectMigrationFiles(
          caseDirectory,
          expectedRoot,
          variant.implementationId
        )
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
