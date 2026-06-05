package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.mustachetest.MustacheTemplateTestSuite

class SqlServiceCodegenMustacheTemplateTestSuite
    extends MustacheTemplateTestSuite(
      backends = List(
        SqlServiceCodegenPythonDbBackend.sqlite,
        SqlServiceCodegenPythonDbBackend.postgres
      )
    )
