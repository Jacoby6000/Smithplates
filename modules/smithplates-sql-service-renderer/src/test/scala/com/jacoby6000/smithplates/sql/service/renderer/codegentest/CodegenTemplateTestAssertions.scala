package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import munit.Assertions

object CodegenTemplateTestAssertions {
  def assertRenderedOutputs(
      testCase: CodegenTemplateTestCase,
      variant: CodegenTemplateVariant,
      expectedFiles: List[CodegenTemplateExpectedFile],
      rendered: Map[String, String]
  ): Unit = {
    val variantExpectedFiles =
      expectedFiles.filter(expected => variantMatchesExpectedPath(variant, expected.relativePath))

    val missingFiles =
      variantExpectedFiles.filterNot(expected => rendered.contains(toOutputRelativePath(expected.relativePath)))
    if (missingFiles.nonEmpty) {
      val renderedListing = rendered.keys.toList.sorted.mkString(", ")
      val missingListing  = missingFiles.map(_.relativePath).sorted.mkString(", ")
      Assertions.fail(
        s"""Test case '${testCase.name}' (${variant.resourcePath}): expected output file(s) were not generated.
           |Missing: $missingListing
           |Generated: ${if (renderedListing.isEmpty) "<none>" else renderedListing}
           |""".stripMargin
      )
    }

    val expectedOutputPaths =
      variantExpectedFiles.map(expected => toOutputRelativePath(expected.relativePath)).toSet
    val unexpectedFiles     =
      rendered.keys.filter(path => variantMatchesOutputPath(variant, path)).toSet -- expectedOutputPaths
    if (unexpectedFiles.nonEmpty) {
      val unexpectedListing = unexpectedFiles.toList.sorted.mkString(", ")
      Assertions.fail(
        s"""Test case '${testCase.name}' (${variant.resourcePath}): unexpected output file(s) were generated.
           |Unexpected: $unexpectedListing
           |Expected: ${variantExpectedFiles.map(_.relativePath).sorted.mkString(", ")}
           |""".stripMargin
      )
    }

    variantExpectedFiles.foreach { expected =>
      val outputRelativePath = toOutputRelativePath(expected.relativePath)
      val actual             = rendered.getOrElse(
        outputRelativePath,
        Assertions.fail(
          s"Test case '${testCase.name}' (${variant.resourcePath}): internal error, missing rendered file '$outputRelativePath'"
        )
      )

      if (actual != expected.content) {
        val outputRelativePath = toOutputRelativePath(expected.relativePath)
        val resourcePath       =
          s"${testCase.caseDirectory.resolve(CodegenTemplateTestDiscovery.ExpectedDirectoryName).resolve(outputRelativePath)}"
        Assertions.fail(
          TextContentDiff.formatMismatch(resourcePath, expected.content, actual)
        )
      }
    }
  }

  private def toOutputRelativePath(expectedRelativePath: String): String =
    if (expectedRelativePath.startsWith(s"${CodegenTemplateTestDiscovery.ExpectedDirectoryName}/")) {
      expectedRelativePath.stripPrefix(s"${CodegenTemplateTestDiscovery.ExpectedDirectoryName}/")
    } else {
      expectedRelativePath
    }

  private def variantMatchesExpectedPath(variant: CodegenTemplateVariant, expectedRelativePath: String): Boolean = {
    val outputRelativePath = toOutputRelativePath(expectedRelativePath)
    variantMatchesOutputPath(variant, outputRelativePath)
  }

  private def variantMatchesOutputPath(variant: CodegenTemplateVariant, outputRelativePath: String): Boolean =
    variant.matchesGeneratedOutputPath(outputRelativePath)
}
