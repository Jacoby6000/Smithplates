package com.jacoby6000.smithy.stache.mustachetest

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

object MustacheTemplateTestDiscovery {
  val ResourceRoot: String        = "mustache-template-tests"
  val SmithyFileName: String      = "smithy-files.smithy"
  val UnsupportedFileName: String = "unsupported.md"

  def discover(classLoader: ClassLoader, variants: Set[MustacheTemplateVariant]): List[MustacheTemplateTestCase] = {
    val rootDirectory = classpathDirectory(classLoader, ResourceRoot)
    if (!Files.isDirectory(rootDirectory)) {
      return Nil
    }

    Files
      .list(rootDirectory)
      .iterator()
      .asScala
      .filter(Files.isDirectory(_))
      .map(_.getFileName.toString)
      .toList
      .sorted
      .map(testName => loadTestCase(classLoader, testName, variants))
  }

  def isVariantUnsupported(
      testCaseBasePath: String,
      variant: MustacheTemplateVariant,
      classLoader: ClassLoader
  ): Boolean =
    classpathResourceExists(classLoader, s"$testCaseBasePath/${variant.resourcePath}/$UnsupportedFileName")

  def warnMissingVariantExpectations(
      testCase: MustacheTemplateTestCase,
      variant: MustacheTemplateVariant,
      classLoader: ClassLoader
  ): Unit = {
    if (isVariantUnsupported(testCase.resourceBasePath, variant, classLoader)) {
      return
    }

    val hasExpectedOutputs = testCase.expectedOutputsByVariant.get(variant).exists(_.nonEmpty)
    if (!hasExpectedOutputs) {
      Console.err.println(
        s"WARNING: Test case '${testCase.name}' has no expected output data for variant ${variant.resourcePath} under ${testCase.resourceBasePath}"
      )
    }
  }

  private def loadTestCase(
      classLoader: ClassLoader,
      testName: String,
      variants: Set[MustacheTemplateVariant]
  ): MustacheTemplateTestCase = {
    val basePath           = s"$ResourceRoot/$testName"
    val smithyResourcePath = s"$basePath/smithy/$SmithyFileName"
    val smithyContent      = readClasspathResource(classLoader, smithyResourcePath)

    val expectedOutputsByVariant =
      variants.toList.sorted.map { variant =>
        variant -> listExpectedFiles(classLoader, basePath, variant)
      }.toMap

    MustacheTemplateTestCase(
      name = testName,
      resourceBasePath = basePath,
      smithyModelId = smithyResourcePath,
      smithyContent = smithyContent,
      expectedOutputsByVariant = expectedOutputsByVariant
    )
  }

  private def listExpectedFiles(
      classLoader: ClassLoader,
      testCaseBasePath: String,
      variant: MustacheTemplateVariant
  ): List[MustacheTemplateExpectedFile] = {
    if (isVariantUnsupported(testCaseBasePath, variant, classLoader)) {
      return Nil
    }

    val caseRootPath                 = testCaseBasePath
    val serviceTypeRootPath          = s"$testCaseBasePath/${variant.serviceTypeResourcePath}"
    val implementationRootPath       = s"$testCaseBasePath/${variant.resourcePath}"
    val implementationTestOutputPath = s"$testCaseBasePath/${variant.testOutputResourcePath}"

    val sharedModelFiles        =
      listFilesRelativeToCaseRoot(
        classLoader,
        caseRootPath,
        s"$serviceTypeRootPath/model"
      )
    val sharedServiceTypeFiles  =
      listFilesDirectlyInDirectoryRelativeToCaseRoot(
        classLoader,
        caseRootPath,
        serviceTypeRootPath
      )
    val implementationFiles     =
      listFilesDirectlyInDirectoryRelativeToCaseRoot(
        classLoader,
        caseRootPath,
        implementationRootPath
      )
    val implementationTestFiles =
      listFilesDirectlyInDirectoryRelativeToCaseRoot(
        classLoader,
        caseRootPath,
        implementationTestOutputPath
      )

    (sharedModelFiles ++ sharedServiceTypeFiles ++ implementationFiles ++ implementationTestFiles)
      .sortBy(_.relativePath)
  }

  private def listFilesRelativeToCaseRoot(
      classLoader: ClassLoader,
      caseRootPath: String,
      directoryPath: String
  ): List[MustacheTemplateExpectedFile] =
    if (!classpathResourceExists(classLoader, directoryPath)) {
      Nil
    } else {
      val caseRoot  = classpathDirectory(classLoader, caseRootPath)
      val directory = classpathDirectory(classLoader, directoryPath)
      Files
        .walk(directory)
        .iterator()
        .asScala
        .filter(Files.isRegularFile(_))
        .filter(path => path.getFileName.toString != UnsupportedFileName)
        .map { path =>
          val relativePath =
            caseRoot.relativize(path).toString.replace('\\', '/')
          MustacheTemplateExpectedFile(
            relativePath = relativePath,
            content = Files.readString(path, StandardCharsets.UTF_8)
          )
        }
        .toList
    }

  private def listFilesDirectlyInDirectoryRelativeToCaseRoot(
      classLoader: ClassLoader,
      caseRootPath: String,
      directoryPath: String
  ): List[MustacheTemplateExpectedFile] =
    if (!classpathResourceExists(classLoader, directoryPath)) {
      Nil
    } else {
      val caseRoot  = classpathDirectory(classLoader, caseRootPath)
      val directory = classpathDirectory(classLoader, directoryPath)
      Files
        .list(directory)
        .iterator()
        .asScala
        .filter(Files.isRegularFile(_))
        .filter(path => path.getFileName.toString != UnsupportedFileName)
        .map { path =>
          val relativePath =
            caseRoot.relativize(path).toString.replace('\\', '/')
          MustacheTemplateExpectedFile(
            relativePath = relativePath,
            content = Files.readString(path, StandardCharsets.UTF_8)
          )
        }
        .toList
    }

  private def classpathDirectory(classLoader: ClassLoader, resourcePath: String): Path = {
    val url = classLoader.getResource(resourcePath)
    if (url == null) {
      throw new IllegalStateException(s"Resource directory not found on classpath: $resourcePath")
    }
    Paths.get(url.toURI)
  }

  private def classpathResourceExists(classLoader: ClassLoader, resourcePath: String): Boolean =
    Option(classLoader.getResource(resourcePath)).isDefined

  private def readClasspathResource(classLoader: ClassLoader, path: String): String = {
    val stream =
      Option(classLoader.getResourceAsStream(path)).getOrElse {
        throw new IllegalStateException(s"Resource not found on classpath: $path")
      }
    try readStream(stream)
    finally stream.close()
  }

  private def readStream(stream: InputStream): String =
    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
}
