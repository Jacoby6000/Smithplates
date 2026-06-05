package com.jacoby6000.smithy.stache.sql

import cats.data.Validated
import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.shared.SqlShared
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlIrExtractor {
  private val columnTypeConverter: ColumnTypeConverter = SmithyColumnTypeConverter

  def extractOrThrow(model: Model): SqlSchema =
    extract(model) match {
      case Validated.Valid(schema)   => schema
      case Validated.Invalid(errors) =>
        throw new IllegalStateException(SqlValidated.toPluginExceptionMessage(errors))
    }

  def extract(model: Model): SqlValidated[SqlSchema] =
    model.getStructureShapes.asScala.toList
      .filter(SmithySqlTraitAccess.sqlTableStructure(_).isDefined)
      .traverse(extractTable(_, model))
      .map(buildSchema)

  private def buildSchema(tables: List[SqlTable]): SqlSchema =
    SqlSchema(
      tables = tables,
      relationships = tables.flatMap(relationshipsForTable)
    )

  private def relationshipsForTable(table: SqlTable): List[SqlRelationship] =
    table.foreignKeys.map { foreignKey =>
      SqlRelationship(
        sourceTable = table.shapeId,
        sourceColumn = foreignKey.column,
        targetTable = foreignKey.referencesShape,
        targetColumn = foreignKey.referencesColumn,
        cardinality = foreignKey.cardinality
      )
    }

  private def extractTable(structure: StructureShape, model: Model): SqlValidated[SqlTable] = {
    val members = com.jacoby6000.smithy.stache.sql.shared.SqlTableMemberOrdering.orderedMembers(structure)

    com.jacoby6000.smithy.stache.sql.shared.SqlTableMemberOrdering.validate(structure).andThen { _ =>
      extractIndexes(structure, members).andThen { indexes =>
        requireTableName(structure).andThen { tableName =>
          (
            members.traverse { case (memberName, member) =>
              SqlValidated.fromEither(toColumn(memberName, member, model, tableName))
            },
            members.flatTraverse { case (memberName, member) =>
              SmithySqlTraitAccess
                .sqlForeignKey(member)
                .traverse { foreignKeyTrait =>
                  SqlValidated.fromEither(
                    parseForeignKeyReference(
                      model,
                      structure,
                      SmithySqlTraitAccess.columnName(memberName, member),
                      foreignKeyTrait.getReferences,
                      foreignKeyTrait.getColumn.toScala,
                      uniqueForeignKeyColumns(indexes)
                    )
                  )
                }
                .map(_.toList)
            }
          ).mapN { (columns, foreignKeys) =>
            val primaryKeys = primaryKeyColumnNames(members)
            (tableName, columns, primaryKeys, foreignKeys)
          }.andThen { case (tableName, columns, primaryKeys, foreignKeys) =>
            SqlValidated
              .requireNonEmpty(primaryKeys, MissingPrimaryKey(structure.getId, tableName))
              .map(_ =>
                SqlTable(
                  name = tableName,
                  shapeId = structure.getId,
                  columns = columns,
                  primaryKeys = primaryKeys,
                  foreignKeys = foreignKeys,
                  indexes = indexes
                ))
          }
        }
      }
    }
  }

  private def extractIndexes(
      structure: StructureShape,
      members: List[(String, MemberShape)]
  ): SqlValidated[List[SqlIndex]] =
    members
      .traverse { case (memberName, member) =>
        val hasIndex       = SmithySqlTraitAccess.sqlIndex(member).isDefined
        val hasUniqueIndex = SmithySqlTraitAccess.sqlUniqueIndex(member).isDefined

        (hasIndex, hasUniqueIndex) match {
          case (true, true)   =>
            ConflictingSqlIndexTraits(structure.getId, memberName).invalidNel
          case (false, false) =>
            None.validNel
          case (true, false)  =>
            Some(
              indexFromMember(
                memberName,
                member,
                SmithySqlTraitAccess.sqlIndex(member).flatMap(_.getName.toScala),
                unique = false
              )
            ).validNel
          case (false, true)  =>
            Some(
              indexFromMember(
                memberName,
                member,
                SmithySqlTraitAccess.sqlUniqueIndex(member).flatMap(_.getName.toScala),
                unique = true
              )
            ).validNel
        }
      }
      .map(_.flatten)

  private def indexFromMember(
      memberName: String,
      member: MemberShape,
      name: Option[String],
      unique: Boolean
  ): SqlIndex =
    SqlIndex(
      name = SqlShared.trimmedNonEmpty(name),
      columns = List(SmithySqlTraitAccess.columnName(memberName, member)),
      unique = unique
    )

  private def uniqueForeignKeyColumns(indexes: List[SqlIndex]): Set[String] =
    indexes.filter(_.unique).flatMap(_.columns).toSet

  private def requireTableName(structure: StructureShape): SqlValidated[String] =
    SmithySqlTraitAccess
      .sqlTableStructure(structure)
      .flatMap(tableTrait => SqlShared.trimmedNonEmpty(tableTrait.getName))
      .map(SqlValidated.valid)
      .getOrElse(SqlValidated.invalid(MissingSqlTableName(structure.getId)))

  private def primaryKeyColumnNames(members: List[(String, MemberShape)]): List[String] =
    members.collect {
      case (memberName, member) if SmithySqlTraitAccess.sqlPrimaryKey(member) =>
        SmithySqlTraitAccess.columnName(memberName, member)
    }

  private def toColumn(
      memberName: String,
      member: MemberShape,
      model: Model,
      tableName: String
  ): Either[SqlSchemaError, SqlColumn] = {
    val columnName     = SmithySqlTraitAccess.columnName(memberName, member)
    val autoGeneration = SmithySqlTraitAccess.autoGeneration(member)
    columnTypeConverter
      .fromSmithyMember(model, member)
      .map { columnType =>
        SqlColumn(
          name = columnName,
          columnType = columnType,
          nullable = !SmithySqlTraitAccess.sqlPrimaryKey(member) && autoGeneration.isEmpty,
          autoGeneration = autoGeneration
        )
      }
      .leftMap(_.toSchemaError(columnName, tableName))
  }

  private def parseForeignKeyReference(
      model: Model,
      sourceShape: StructureShape,
      columnName: String,
      reference: String,
      explicitColumn: Option[String],
      uniqueForeignKeyColumns: Set[String]
  ): Either[SqlSchemaError, SqlForeignKey] = {
    val invalidReference = InvalidForeignKeyReference(sourceShape.getId, columnName, reference)

    for {
      targetId         <- parseShapeId(reference).left.map(_ => invalidReference)
      targetStructure  <- lookupSqlTableStructure(model, targetId).toRight(invalidReference)
      referencesColumn <- SqlShared.trimmedNonEmpty(explicitColumn) match {
                            case Some(column) => Right(column)
                            case None         => solePrimaryKeyColumnName(targetStructure)
                          }
    } yield SqlForeignKey(
      column = columnName,
      referencesShape = targetId,
      referencesColumn = referencesColumn,
      cardinality =
        if (uniqueForeignKeyColumns.contains(columnName)) SqlRelationshipCardinality.OneToOne
        else SqlRelationshipCardinality.ManyToOne
    )
  }

  private def parseShapeId(reference: String): Either[String, ShapeId] =
    Either.catchOnly[RuntimeException](ShapeId.from(reference)).left.map(_ => reference)

  private def lookupSqlTableStructure(model: Model, shapeId: ShapeId): Option[StructureShape] =
    for {
      shape    <- model.getShape(shapeId).toScala if shape.isStructureShape
      structure = shape.asStructureShape.get()
      if SmithySqlTraitAccess.sqlTableStructure(structure).isDefined
    } yield structure

  private def solePrimaryKeyColumnName(structure: StructureShape): Either[SqlSchemaError, String] =
    primaryKeyColumnNames(structure.getAllMembers.asScala.toList) match {
      case single :: Nil => Right(single)
      case _             =>
        Left(
          MissingPrimaryKey(
            structure.getId,
            SmithySqlTraitAccess
              .sqlTableStructure(structure)
              .flatMap(tableTrait => SqlShared.trimmedNonEmpty(tableTrait.getName))
              .getOrElse(structure.getId.getName)
          )
        )
    }
}
