package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegentest.CodegenTemplateTestSuite
import com.jacoby6000.smithplates.codegentest.CodegenTemplateVariant

class SqlServiceCodegenTemplateTestSuite
    extends CodegenTemplateTestSuite(
      languageId = "python",
      variants = Set(
        CodegenTemplateVariant("python", "db", "sqlite"),
        CodegenTemplateVariant("python", "db", "postgres")
      )
    )
