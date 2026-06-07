package com.jacoby6000.smithy.stache.codegentest

import munit.FunSuite

abstract class CodegenTemplateTestSuite(backends: List[CodegenTemplateBackend]) extends FunSuite {
  private lazy val testCases: List[CodegenTemplateTestCase] =
    CodegenTemplateTestDiscovery.discover(
      getClass.getClassLoader,
      backends.map(_.variant).toSet
    )

  backends.foreach { backend =>
    testCases.foreach { testCase =>
      CodegenTemplateTestDiscovery.warnMissingVariantExpectations(
        testCase,
        backend.variant,
        getClass.getClassLoader
      )

      val expectedFiles =
        testCase.expectedOutputsByVariant.getOrElse(backend.variant, Nil)
      val unsupported   =
        CodegenTemplateTestDiscovery.isVariantUnsupported(
          testCase.resourceBasePath,
          backend.variant,
          getClass.getClassLoader
        )

      if (!unsupported && expectedFiles.nonEmpty) {
        test(s"${testCase.name} - ${backend.variant.resourcePath}") {
          val model    =
            backend
              .loadModel(testCase)
              .fold(
                message =>
                  fail(s"Model assembly failed for '${testCase.name}' (${backend.variant.resourcePath}): $message"),
                identity
              )
          val rendered =
            backend
              .render(testCase, model)
              .fold(
                message => fail(s"Rendering failed for '${testCase.name}' (${backend.variant.resourcePath}): $message"),
                identity
              )
          CodegenTemplateTestAssertions.assertRenderedOutputs(testCase, backend.variant, rendered)
          backend.validateRenderedOutputs(testCase, rendered)
        }
      }
    }
  }
}
