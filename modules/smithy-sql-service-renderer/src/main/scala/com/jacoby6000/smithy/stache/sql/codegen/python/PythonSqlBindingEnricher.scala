package com.jacoby6000.smithy.stache.sql.codegen.python

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape

object PythonSqlBindingEnricher {
  def enrich(binding: SqlCodegenSqlBinding, dialectKey: String): SqlCodegenSqlBinding =
    binding.copy(
      bindParameters = binding.bindParameters.map(enrichBindParameter(dialectKey, _)),
      resultFields = binding.resultFields.map(enrichResultField)
    )

  def resolveTimestampFormat(model: Model, member: MemberShape): Option[SqlTimestampFormat] =
    SmithyTimestampFormatResolver.resolve(model, member) match {
      case Right(format) => Some(format)
      case Left(_)       => None
    }

  private def enrichBindParameter(dialectKey: String, parameter: SqlCodegenBindParameter): SqlCodegenBindParameter = {
    val bindExpression =
      if (parameter.isJson) {
        s"_json_bind_${parameter.jsonTypeName.get}(${parameter.memberName})"
      } else {
        SqlCodegenTimestampHelpers.bindExpression(
          dialectKey,
          parameter.languageTypeName,
          parameter.timestampFormat.getOrElse(SqlTimestampFormat.Default),
          parameter.memberName
        )
      }
    parameter.copy(bindExpression = Some(bindExpression))
  }

  private def enrichResultField(field: SqlCodegenResultField): SqlCodegenResultField = {
    val rowReader          =
      SqlCodegenTimestampHelpers.rowReaderName(
        field.languageTypeName,
        field.timestampFormat.getOrElse(SqlTimestampFormat.Default)
      )
    val jsonReadExpression =
      if (field.isJson) {
        Some(s"_read_${field.languageTypeName}(row, ${field.columnIndex})")
      } else {
        None
      }
    field.copy(rowReader = Some(rowReader), jsonReadExpression = jsonReadExpression)
  }
}
