package com.jacoby6000.smithy.sql.codegen

import com.jacoby6000.smithy.mustachetest.MustacheTemplateTestSuite

class SqlServiceCodegenMustacheTemplateTestSuite
    extends MustacheTemplateTestSuite(
      backends = List(
        SqlServiceCodegenPythonDbBackend.sqlite,
        SqlServiceCodegenPythonDbBackend.postgres
      )
    )
