package com.jacoby6000.smithplates.plugin

import com.jacoby6000.smithplates.codegentest.CodegenTemplateVariant
import com.jacoby6000.smithplates.plugin.codegentest.CodegenTemplateTestSuite

class SqlServiceCodegenTemplateTestSuite
    extends CodegenTemplateTestSuite(
      languageId = "python",
      variants = Set(
        CodegenTemplateVariant("python", "db", "sqlite"),
        CodegenTemplateVariant("python", "db", "postgres")
      )
    )
