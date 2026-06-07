package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateTestSuite

class SqlServiceCodegenTemplateTestSuite
    extends CodegenTemplateTestSuite(
      backends = List(
        SqlServiceCodegenTemplateBackend.pythonSqlite,
        SqlServiceCodegenTemplateBackend.pythonPostgres
      )
    )
