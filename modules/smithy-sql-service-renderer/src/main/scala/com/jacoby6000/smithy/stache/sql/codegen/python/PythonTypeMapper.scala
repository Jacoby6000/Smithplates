package com.jacoby6000.smithy.stache.sql.codegen.python

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.codegen.SqlCodegenMemberRole
import com.jacoby6000.smithy.stache.sql.codegen.SqlCodegenShapeGraph
import com.jacoby6000.smithy.stache.sql.codegen.language.LanguageTypeMapper
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

object PythonTypeMapper extends LanguageTypeMapper {
  def resolveLanguageTypeName(model: Model, shape: Shape): String =
    new PythonLanguageTypeNameVisitor(model).apply(shape)

  def resolveOutputLanguageTypeName(shapeId: ShapeId): String =
    if (SqlCodegenShapeGraph.isPreludeShape(shapeId)) {
      resolvePrimitiveLanguageTypeName(shapeId)
    } else {
      shapeId.getName
    }

  def resolvePrimitiveLanguageTypeName(shapeId: ShapeId): String =
    shapeId.getName match {
      case "String"     => "str"
      case "Integer"    => "int"
      case "Long"       => "int"
      case "Float"      => "float"
      case "Double"     => "float"
      case "BigDecimal" => "Decimal"
      case "BigInteger" => "int"
      case "Boolean"    => "bool"
      case "Blob"       => "bytes"
      case "Timestamp"  => "datetime"
      case "Document"   => "Any"
      case "Unit"       => "None"
      case other        => other
    }

  def isOptionalMember(member: MemberShape, role: SqlCodegenMemberRole): Boolean =
    role match {
      case SqlCodegenMemberRole.General     =>
        !member.isRequired
      case SqlCodegenMemberRole.SqlTableRow =>
        !member.isRequired &&
        !member.sqlPrimaryKey &&
        member.autoGeneration.isEmpty
    }
}
