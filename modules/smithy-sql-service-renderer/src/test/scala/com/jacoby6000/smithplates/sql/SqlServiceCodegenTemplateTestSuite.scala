package com.jacoby6000.smithplates.sql.codegen

import com.jacoby6000.smithplates.codegentest.CodegenTemplateTestSuite

class SqlServiceCodegenTemplateTestSuite
    extends CodegenTemplateTestSuite(
      backends = List(
        SqlServiceCodegenTemplateBackend.pythonSqlite,
        SqlServiceCodegenTemplateBackend.pythonPostgres
      )
    )
