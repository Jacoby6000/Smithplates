package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.plugin.codegentest.CodegenTemplateTestSuite
import com.jacoby6000.smithplates.sql.service.renderer.codegentest.CodegenTemplateVariant

class SqlServiceCodegenTemplateTestSuite
    extends CodegenTemplateTestSuite(
      languageId = "python",
      variants = Set(
        CodegenTemplateVariant("python", "db", "sqlite"),
        CodegenTemplateVariant("python", "db", "postgres")
      )
    )
