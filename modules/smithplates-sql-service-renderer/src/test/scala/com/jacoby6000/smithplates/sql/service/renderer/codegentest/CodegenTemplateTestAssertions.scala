package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import munit.Assertions

object CodegenTemplateTestAssertions {
  def assertRenderedOutputs(
      testCase: CodegenTemplateTestCase,
      variant: CodegenTemplateVariant,
      expectedFiles: List[CodegenTemplateExpectedFile],
      rendered: Map[String, String]
  ): Unit = {
    val namespacePathPrefix  = SmithyNamespaceTestSupport.namespacePathPrefix(testCase.smithyNamespace)
    val variantLabel         = variant.resourcePath(namespacePathPrefix)
    val variantExpectedFiles =
      expectedFiles.filter(expected =>
        internal.variantMatchesExpectedPath(variant, expected.relativePath, namespacePathPrefix))

    val missingFiles =
      variantExpectedFiles.filterNot(expected =>
        rendered.contains(internal.toOutputRelativePath(expected.relativePath)))
    if (missingFiles.nonEmpty) {
      val renderedListing = rendered.keys.toList.sorted.mkString(", ")
      val missingListing  = missingFiles.map(_.relativePath).sorted.mkString(", ")
      Assertions.fail(
        s"""Test case '${testCase.name}' ($variantLabel): expected output file(s) were not generated.
           |Missing: $missingListing
           |Generated: ${if (renderedListing.isEmpty) "<none>" else renderedListing}
           |""".stripMargin
      )
    }

    val expectedOutputPaths =
      variantExpectedFiles.map(expected => internal.toOutputRelativePath(expected.relativePath)).toSet
    val unexpectedFiles     =
      rendered.keys
        .filter(path => internal.variantMatchesOutputPath(variant, path, namespacePathPrefix))
        .toSet -- expectedOutputPaths
    if (unexpectedFiles.nonEmpty) {
      val unexpectedListing = unexpectedFiles.toList.sorted.mkString(", ")
      Assertions.fail(
        s"""Test case '${testCase.name}' ($variantLabel): unexpected output file(s) were generated.
           |Unexpected: $unexpectedListing
           |Expected: ${variantExpectedFiles.map(_.relativePath).sorted.mkString(", ")}
           |""".stripMargin
      )
    }

    variantExpectedFiles.foreach { expected =>
      val outputRelativePath = internal.toOutputRelativePath(expected.relativePath)
      val actual             = rendered.getOrElse(
        outputRelativePath,
        Assertions.fail(
          s"Test case '${testCase.name}' ($variantLabel): internal error, missing rendered file '$outputRelativePath'"
        )
      )

      if (actual != expected.content) {
        val resourcePath =
          s"${testCase.caseDirectory.resolve(CodegenTemplateTestDiscovery.ExpectedDirectoryName).resolve(outputRelativePath)}"
        Assertions.fail(
          TextContentDiff.formatMismatch(resourcePath, expected.content, actual)
        )
      }
    }
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def toOutputRelativePath(expectedRelativePath: String): String =
      if (expectedRelativePath.startsWith(s"${CodegenTemplateTestDiscovery.ExpectedDirectoryName}/")) {
        expectedRelativePath.stripPrefix(s"${CodegenTemplateTestDiscovery.ExpectedDirectoryName}/")
      } else {
        expectedRelativePath
      }

    def variantMatchesExpectedPath(
        variant: CodegenTemplateVariant,
        expectedRelativePath: String,
        namespacePathPrefix: String
    ): Boolean = {
      val outputRelativePath = toOutputRelativePath(expectedRelativePath)
      variantMatchesOutputPath(variant, outputRelativePath, namespacePathPrefix)
    }

    def variantMatchesOutputPath(
        variant: CodegenTemplateVariant,
        outputRelativePath: String,
        namespacePathPrefix: String
    ): Boolean =
      variant.matchesGeneratedOutputPath(outputRelativePath, namespacePathPrefix)
  }
}
