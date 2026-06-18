package com.jacoby6000.smithplates.plugin.codegentest

import com.jacoby6000.smithplates.sql.service.renderer.codegentest.CodegenTemplateTestAssertions
import com.jacoby6000.smithplates.sql.service.renderer.codegentest.CodegenTemplateTestCase
import com.jacoby6000.smithplates.sql.service.renderer.codegentest.CodegenTemplateTestDiscovery
import com.jacoby6000.smithplates.sql.service.renderer.codegentest.CodegenTemplateVariant
import com.jacoby6000.smithplates.sql.service.renderer.codegentest.SmithyNamespaceTestSupport
import munit.FunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/** Runs each fixture's `smithy-build.json` once, then compares `out/` to `expected/` per variant.
  *
  * Single language-agnostic golden suite covering every codegen variant. Adding a new language (or implementation) is
  * just adding a [[CodegenTemplateVariant]] to `variants`; discovery, build, and comparison registration are grouped by
  * `languageId` so no new suite class is required.
  */
class CodegenTemplateTestSuite extends FunSuite {
  override val munitTimeout = 120.seconds

  private val variants: Set[CodegenTemplateVariant] = Set(
    CodegenTemplateVariant("python", "http", "server"),
    CodegenTemplateVariant("python", "http", "client"),
    CodegenTemplateVariant("python", "db", "sqlite"),
    CodegenTemplateVariant("python", "db", "postgres")
  )

  private val buildOutputs =
    new ConcurrentHashMap[String, Either[String, Path]]()

  private lazy val repoRoot: Path =
    Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize

  private val variantsByLanguage: List[(String, Set[CodegenTemplateVariant])] =
    variants.groupBy(_.languageId).toList.sortBy(_._1)

  private def hasActiveVariantExpectations(
      testCase: CodegenTemplateTestCase,
      languageVariants: Set[CodegenTemplateVariant]
  ): Boolean =
    languageVariants.exists { variant =>
      val expectedFiles = testCase.expectedOutputsByVariant.getOrElse(variant, Nil)
      !CodegenTemplateTestDiscovery.isVariantUnsupported(testCase, variant) && expectedFiles.nonEmpty
    }

  variantsByLanguage.foreach { case (languageId, languageVariants) =>
    val testCases =
      CodegenTemplateTestDiscovery.discover(repoRoot, languageId, languageVariants)

    val casesRequiringBuild =
      testCases.filter(testCase => hasActiveVariantExpectations(testCase, languageVariants))

    casesRequiringBuild.foreach { testCase =>
      test(s"build - ${testCase.name}") {
        val result =
          SmithyBuildTemplateRunner.run(testCase.caseDirectory, getClass.getClassLoader)
        buildOutputs.put(testCase.name, result)
        result.fold(
          message => fail(s"Smithy build failed for '${testCase.name}': $message"),
          _ => ()
        )
      }
    }

    languageVariants.toList.sorted.foreach { variant =>
      testCases.foreach { testCase =>
        CodegenTemplateTestDiscovery.warnMissingVariantExpectations(testCase, variant)

        val expectedFiles =
          testCase.expectedOutputsByVariant.getOrElse(variant, Nil)
        val unsupported   =
          CodegenTemplateTestDiscovery.isVariantUnsupported(testCase, variant)

        if (!unsupported && expectedFiles.nonEmpty) {
          test(
            s"${testCase.name} - ${variant.resourcePath(SmithyNamespaceTestSupport.namespacePathPrefix(testCase.smithyNamespace))}") {
            val outputDirectory = outputDirectoryFor(testCase)
            val actualOutputs   = readBuildOutputs(outputDirectory)
            CodegenTemplateTestAssertions.assertRenderedOutputs(
              testCase,
              variant,
              expectedFiles,
              actualOutputs
            )
          }
        }
      }
    }
  }

  private def outputDirectoryFor(testCase: CodegenTemplateTestCase): Path =
    Option(buildOutputs.get(testCase.name)) match {
      case None                         =>
        fail(
          s"Missing Smithy build output for '${testCase.name}'; expected a preceding build - ${testCase.name} test"
        )
      case Some(Left(message))          =>
        fail(s"Smithy build failed for '${testCase.name}': $message")
      case Some(Right(outputDirectory)) =>
        outputDirectory
    }

  private def readBuildOutputs(outputDirectory: Path): Map[String, String] =
    if (!Files.isDirectory(outputDirectory)) {
      Map.empty
    } else {
      Files
        .walk(outputDirectory)
        .iterator()
        .asScala
        .filter(Files.isRegularFile(_))
        .flatMap { path =>
          val relativePath =
            normalizeBuildOutputPath(
              outputDirectory.relativize(path).toString.replace('\\', '/')
            )
          relativePath.map(_ -> Files.readString(path, StandardCharsets.UTF_8))
        }
        .toMap
    }

  /** Smithy writes plugin artifacts under `{projection}/{pluginName}/`; strip that prefix for comparison. */
  private def normalizeBuildOutputPath(relativePath: String): Option[String] = {
    val pluginPrefix = s"source/${SmithyBuildTemplateRunner.PluginName}/"
    if (relativePath.startsWith(pluginPrefix)) {
      Some(relativePath.stripPrefix(pluginPrefix))
    } else if (relativePath.startsWith("source/")) {
      None
    } else {
      Some(relativePath)
    }
  }
}
