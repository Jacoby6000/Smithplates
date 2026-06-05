package com.jacoby6000.smithy.mustachetest

import munit.Assertions

object MustacheTemplateTestAssertions {
  def assertRenderedOutputs(
      testCase: MustacheTemplateTestCase,
      variant: MustacheTemplateVariant,
      rendered: Map[String, String]
  ): Unit = {
    val expectedFiles =
      testCase.expectedOutputsByVariant.getOrElse(variant, Nil)

    val missingFiles = expectedFiles.filterNot(expected => rendered.contains(expected.relativePath))
    if (missingFiles.nonEmpty) {
      val renderedListing = rendered.keys.toList.sorted.mkString(", ")
      val missingListing = missingFiles.map(_.relativePath).sorted.mkString(", ")
      Assertions.fail(
        s"""Test case '${testCase.name}' (${variant.resourcePath}): expected output file(s) were not generated.
           |Missing: $missingListing
           |Generated: ${if (renderedListing.isEmpty) "<none>" else renderedListing}
           |""".stripMargin
      )
    }

    val unexpectedFiles =
      rendered.keys.toSet -- expectedFiles.map(_.relativePath).toSet
    if (unexpectedFiles.nonEmpty) {
      val unexpectedListing = unexpectedFiles.toList.sorted.mkString(", ")
      Assertions.fail(
        s"""Test case '${testCase.name}' (${variant.resourcePath}): unexpected output file(s) were generated.
           |Unexpected: $unexpectedListing
           |Expected: ${expectedFiles.map(_.relativePath).sorted.mkString(", ")}
           |""".stripMargin
      )
    }

    expectedFiles.foreach { expected =>
      val actual = rendered.getOrElse(
        expected.relativePath,
        Assertions.fail(
          s"Test case '${testCase.name}' (${variant.resourcePath}): internal error, missing rendered file '${expected.relativePath}'"
        )
      )

      if (actual != expected.content) {
        val resourcePath =
          s"${testCase.resourceBasePath}/${expected.relativePath}"
        Assertions.fail(
          TextContentDiff.formatMismatch(resourcePath, expected.content, actual)
        )
      }
    }
  }
}
