package com.jacoby6000.smithplates.sql.service

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** Catalog of @sqlTable structure members used for query validation. */
private[service] object SqlTableMemberCatalog {
  final case class TableMemberInfo(
      memberName: String,
      columnName: String,
      required: Boolean,
      autoGeneration: Option[SqlAutoGeneration],
      isPrimaryKey: Boolean
  ) {
    def databaseManagedOnInsert: Boolean =
      autoGeneration.isDefined

    def databaseManagedOnUpdate: Boolean =
      autoGeneration.exists {
        case SqlAutoUuid | SqlAutoIncrement => false
        case _                              => true
      }
  }

  def membersFor(structure: StructureShape): List[TableMemberInfo] =
    SqlTableMemberOrdering
      .orderedMembers(structure)
      .map { case (memberName, member) =>
        TableMemberInfo(
          memberName = memberName,
          columnName = member.sqlColumnName(memberName),
          required = member.isRequired,
          autoGeneration = member.autoGeneration,
          isPrimaryKey = member.sqlPrimaryKey
        )
      }

  def lookupSqlTableStructure(model: Model, shapeId: ShapeId): Option[StructureShape] =
    for {
      shape    <- model.getShape(shapeId).toScala if shape.isStructureShape
      structure = shape.asStructureShape.get()
      if structure.sqlTable.isDefined
    } yield structure

  def parseShapeId(reference: String): Option[ShapeId] =
    Either.catchOnly[RuntimeException](ShapeId.from(reference)).toOption

  def insertableMembers(tableMembers: List[TableMemberInfo]): List[TableMemberInfo] =
    tableMembers.filterNot(_.databaseManagedOnInsert)

  def updatableSetMembers(tableMembers: List[TableMemberInfo]): List[TableMemberInfo] =
    tableMembers.filter(member => !member.isPrimaryKey && !member.databaseManagedOnUpdate)
}

object SqlQueryExtractor {
  val DerivedStructShapeId: ShapeId = ShapeId.from("smithplates.codegen.sql#DerivedStruct")

  def extract(model: Model, schema: SqlSchema): SqlValidated[SqlQueries] =
    (
      internal.extractInserts(model, schema),
      internal.extractUpdates(model, schema),
      internal.extractDeletes(model, schema),
      internal.extractSelectOnes(model, schema),
      SqlDeriveSelectExtractor.extractDeriveSelects(model, schema)
    ).mapN(SqlQueries(_, _, _, _, _))

  private[service] def deriveSelectOnePrimaryKeyColumns(
      operationShape: ShapeId,
      tableName: String,
      model: Model,
      tableStructure: StructureShape,
      table: SqlTable
  ): SqlValidated[List[SqlQueryColumn]] =
    internal.validateDerivePrimaryKeyColumns(
      operationShape,
      tableName,
      model,
      tableStructure,
      table,
      InvalidDeriveSelectOne(_, _)
    )

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val UnitShapeId: ShapeId    = ShapeId.from("smithy.api#Unit")
    val BooleanShapeId: ShapeId = ShapeId.from("smithy.api#Boolean")

    def extractInserts(model: Model, schema: SqlSchema): SqlValidated[List[SqlInsertQuery]] =
      model.getOperationShapes.asScala.toList
        .flatMap { operation =>
          operation.sqlDeriveInsert.map(insertTrait => (operation, insertTrait))
        }
        .traverse { case (operation, insertTrait) =>
          extractInsert(model, schema, operation, insertTrait.getTargetTable)
        }

    def extractUpdates(model: Model, schema: SqlSchema): SqlValidated[List[SqlUpdateQuery]] =
      (
        extractStructureUpdates(model, schema),
        extractDeriveUpdates(model, schema)
      ).mapN(_ ++ _)

    def extractStructureUpdates(model: Model, schema: SqlSchema): SqlValidated[List[SqlUpdateQuery]] =
      model.getStructureShapes.asScala.toList
        .flatMap { structure =>
          structure.sqlUpdate.map(updateTrait => (structure, updateTrait))
        }
        .traverse { case (structure, updateTrait) =>
          extractStructureUpdate(model, schema, structure, updateTrait.getTableRef)
        }

    def extractDeriveUpdates(model: Model, schema: SqlSchema): SqlValidated[List[SqlUpdateQuery]] =
      model.getOperationShapes.asScala.toList
        .flatMap { operation =>
          operation.sqlDeriveUpdate.map(updateTrait => (operation, updateTrait))
        }
        .traverse { case (operation, updateTrait) =>
          extractDeriveUpdate(model, schema, operation, updateTrait.getTargetTable)
        }

    def extractDeletes(model: Model, schema: SqlSchema): SqlValidated[List[SqlDeleteQuery]] =
      model.getOperationShapes.asScala.toList
        .flatMap { operation =>
          operation.sqlDeriveDelete.map(deleteTrait => (operation, deleteTrait))
        }
        .traverse { case (operation, deleteTrait) =>
          extractDeriveDelete(model, schema, operation, deleteTrait.getTargetTable)
        }

    def extractSelectOnes(model: Model, schema: SqlSchema): SqlValidated[List[SqlSelectOneQuery]] =
      model.getOperationShapes.asScala.toList
        .flatMap { operation =>
          operation.sqlDeriveSelectOne.map(selectTrait => (operation, selectTrait))
        }
        .traverse { case (operation, selectTrait) =>
          SqlDeriveSelectOneExtractor.extract(model, schema, operation, selectTrait)
        }

    def extractInsert(
        model: Model,
        schema: SqlSchema,
        operation: OperationShape,
        targetTable: String
    ): SqlValidated[SqlInsertQuery] = {
      val queryKind      = InvalidQueryTableReference.Kind.Insert
      val operationShape = operation.getId

      SqlSelectTableContext.resolveTable(model, schema, operationShape, targetTable, queryKind).andThen {
        case (table, tableStructure) =>
          requireDerivedStructInput(operation, "sqlDeriveInsert").andThen { _ =>
            rejectRequiredForeignKeyCycle(schema, operationShape, table).andThen { _ =>
              val tableMembers       = SqlTableMemberCatalog.membersFor(tableStructure)
              val tableMembersByName = tableMembers.map(info => info.memberName -> info).toMap
              val insertableMembers  = SqlTableMemberCatalog.insertableMembers(tableMembers)

              resolveInsertReturningColumns(
                model,
                operation,
                tableStructure,
                tableMembersByName,
                table,
                table.name
              ).map { returningColumns =>
                val columns = insertableMembers.map(tableMember =>
                  SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember))
                SqlInsertQuery(
                  shapeId = operationShape,
                  table = table,
                  columns = columns,
                  returningColumns = returningColumns
                )
              }
            }
          }
      }
    }

    def rejectRequiredForeignKeyCycle(
        schema: SqlSchema,
        operationShape: ShapeId,
        table: SqlTable
    ): SqlValidated[Unit] =
      if (SqlTableTree.hasRequiredCycleContaining(schema, table.shapeId)) {
        InvalidDeriveInsert(
          operationShape,
          s"target table '${table.name}' participates in a required foreign-key cycle; derived inserts cannot safely order writes without deferred constraint evaluation"
        ).invalidNel
      } else {
        ().validNel
      }

    def requireDerivedStructInput(operation: OperationShape, deriveTrait: String): SqlValidated[Unit] = {
      val operationShape = operation.getId
      val inputShapeId   = Option(operation.getInputShape).getOrElse(UnitShapeId)

      if (inputShapeId == DerivedStructShapeId) {
        ().validNel
      } else {
        deriveTrait match {
          case "sqlDeriveInsert"    =>
            InvalidDeriveInsert(
              operationShape,
              s"input must be ${DerivedStructShapeId.toString}; codegen expands table-derived members from the @sqlDeriveInsert targetTable"
            ).invalidNel
          case "sqlDeriveUpdate"    =>
            InvalidDeriveUpdate(
              operationShape,
              s"input must be ${DerivedStructShapeId.toString}; codegen expands whereClause and updateFields from the @sqlDeriveUpdate targetTable"
            ).invalidNel
          case "sqlDeriveDelete"    =>
            InvalidDeriveDelete(
              operationShape,
              s"input must be ${DerivedStructShapeId.toString}; codegen expands whereClause from the @sqlDeriveDelete targetTable"
            ).invalidNel
          case "sqlDeriveSelectOne" =>
            InvalidDeriveSelectOne(
              operationShape,
              s"input must be ${DerivedStructShapeId.toString}; codegen expands whereClause from the @sqlDeriveSelectOne targetTable"
            ).invalidNel
          case other                =>
            InvalidPluginConfig(s"unknown derive trait '$other' in requireDerivedStructInput").invalidNel
        }
      }
    }

    def requireBooleanDeriveUpdateOutput(operation: OperationShape, model: Model): SqlValidated[Option[String]] =
      requireBooleanDeriveOutput(
        operation,
        model,
        error =>
          InvalidDeriveUpdate(
            operation.getId,
            s"output must be Boolean or a structure with exactly one Boolean member (false when no row was updated); got '${error.outputShapeId.toString}'"
          )
      )

    def requireBooleanDeriveDeleteOutput(operation: OperationShape, model: Model): SqlValidated[Option[String]] =
      requireBooleanDeriveOutput(
        operation,
        model,
        error =>
          InvalidDeriveDelete(
            operation.getId,
            s"output must be Boolean or a structure with exactly one Boolean member (false when no row was deleted); got '${error.outputShapeId.toString}'"
          )
      )

    final case class UnexpectedDeriveOutputShape(outputShapeId: ShapeId)

    def requireBooleanDeriveOutput(
        operation: OperationShape,
        model: Model,
        toError: UnexpectedDeriveOutputShape => SqlSchemaError
    ): SqlValidated[Option[String]] = {
      val outputShapeId = operation.getOutput.toScala.getOrElse(operation.getOutputShape)

      if (outputShapeId == BooleanShapeId) {
        None.validNel
      } else {
        model
          .getShape(outputShapeId)
          .toScala
          .collect { case structure: StructureShape =>
            val members = structure.getAllMembers.asScala.toList
            members match {
              case (memberName, member) :: Nil if member.getTarget == BooleanShapeId =>
                Some(memberName).validNel
              case _                                                                 =>
                toError(UnexpectedDeriveOutputShape(outputShapeId)).invalidNel
            }
          }
          .getOrElse(toError(UnexpectedDeriveOutputShape(outputShapeId)).invalidNel)
      }
    }

    def extractDeriveUpdate(
        model: Model,
        schema: SqlSchema,
        operation: OperationShape,
        targetTable: String
    ): SqlValidated[SqlUpdateQuery] = {
      val queryKind      = InvalidQueryTableReference.Kind.DeriveUpdate
      val operationShape = operation.getId

      SqlSelectTableContext.resolveTable(model, schema, operationShape, targetTable, queryKind).andThen {
        case (table, tableStructure) =>
          (
            requireDerivedStructInput(operation, "sqlDeriveUpdate"),
            requireBooleanDeriveUpdateOutput(operation, model),
            validateDeriveUpdateColumns(
              operationShape,
              table.name,
              model,
              tableStructure,
              table
            )
          ).mapN { (_, booleanResultMemberName, columns) =>
            val returningColumns =
              SqlTableMemberCatalog
                .membersFor(tableStructure)
                .filter(_.autoGeneration.contains(SqlUpdatedTimestamp))
                .map(tableMember => SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember))
            SqlUpdateQuery(
              shapeId = operationShape,
              table = table,
              setColumns = columns.setColumns,
              whereColumns = columns.whereColumns,
              returningColumns = returningColumns,
              booleanResultMemberName = booleanResultMemberName
            )
          }
      }
    }

    def extractDeriveDelete(
        model: Model,
        schema: SqlSchema,
        operation: OperationShape,
        targetTable: String
    ): SqlValidated[SqlDeleteQuery] = {
      val queryKind      = InvalidQueryTableReference.Kind.DeriveDelete
      val operationShape = operation.getId

      SqlSelectTableContext.resolveTable(model, schema, operationShape, targetTable, queryKind).andThen {
        case (table, tableStructure) =>
          (
            requireDerivedStructInput(operation, "sqlDeriveDelete"),
            requireBooleanDeriveDeleteOutput(operation, model),
            validateDeriveDeleteColumns(operationShape, table.name, model, tableStructure, table)
          ).mapN { (_, booleanResultMemberName, whereColumns) =>
            SqlDeleteQuery(
              shapeId = operationShape,
              table = table,
              whereColumns = whereColumns,
              returningColumns = whereColumns,
              booleanResultMemberName = booleanResultMemberName
            )
          }
      }
    }

    def validateDeriveDeleteColumns(
        operationShape: ShapeId,
        tableName: String,
        model: Model,
        tableStructure: StructureShape,
        table: SqlTable
    ): SqlValidated[List[SqlQueryColumn]] =
      validateDerivePrimaryKeyColumns(
        operationShape,
        tableName,
        model,
        tableStructure,
        table,
        InvalidDeriveDelete(_, _))

    def validateDerivePrimaryKeyColumns(
        operationShape: ShapeId,
        tableName: String,
        model: Model,
        tableStructure: StructureShape,
        table: SqlTable,
        toError: (ShapeId, String) => SqlSchemaError
    ): SqlValidated[List[SqlQueryColumn]] = {
      val primaryKeyMembers = SqlTableMemberCatalog.membersFor(tableStructure).filter(_.isPrimaryKey)

      if (primaryKeyMembers.isEmpty) {
        toError(
          operationShape,
          s"table '$tableName' has no @sqlPrimaryKey members for whereClause"
        ).invalidNel
      } else {
        primaryKeyMembers
          .map(tableMember => SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember))
          .validNel
      }
    }

    final case class DeriveUpdateColumns(
        whereColumns: List[SqlQueryColumn],
        setColumns: List[SqlQueryColumn]
    )

    def validateDeriveUpdateColumns(
        operationShape: ShapeId,
        tableName: String,
        model: Model,
        tableStructure: StructureShape,
        table: SqlTable
    ): SqlValidated[DeriveUpdateColumns] = {
      val tableMembers      = SqlTableMemberCatalog.membersFor(tableStructure)
      val primaryKeyMembers = tableMembers.filter(_.isPrimaryKey)
      val setMembers        = SqlTableMemberCatalog.updatableSetMembers(tableMembers)

      if (primaryKeyMembers.isEmpty) {
        InvalidDeriveUpdate(
          operationShape,
          s"table '$tableName' has no @sqlPrimaryKey members for whereClause"
        ).invalidNel
      } else if (setMembers.isEmpty) {
        InvalidDeriveUpdate(
          operationShape,
          s"table '$tableName' has no updatable columns for updateFields"
        ).invalidNel
      } else {
        DeriveUpdateColumns(
          whereColumns = primaryKeyMembers.map(tableMember =>
            SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember)),
          setColumns =
            setMembers.map(tableMember => SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember))
        ).validNel
      }
    }

    def resolveInsertReturningColumns(
        model: Model,
        operation: OperationShape,
        tableStructure: StructureShape,
        tableMembersByName: Map[String, SqlTableMemberCatalog.TableMemberInfo],
        table: SqlTable,
        tableName: String
    ): SqlValidated[List[SqlQueryColumn]] = {
      val operationShape    = operation.getId
      val outputShapeId     = operation.getOutput.toScala.getOrElse(operation.getOutputShape)
      val primaryKeyTargets = primaryKeyMemberTargets(tableStructure)

      if (outputShapeId == UnitShapeId) {
        SqlValidated.invalid(
          InvalidDeriveInsert(operationShape, deriveInsertOutputGuidance(primaryKeyTargets))
        )
      } else {
        model.getShape(outputShapeId).toScala match {
          case None                                  =>
            SqlValidated.invalid(
              InvalidDeriveInsert(
                operationShape,
                s"output shape '${outputShapeId.toString}' is not defined in the model"
              )
            )
          case Some(shape) if shape.isStructureShape =>
            val outputStructure = shape.asStructureShape.get()
            outputStructure.getAllMembers.asScala.toList
              .traverse { case (memberName, _) =>
                tableMembersByName.get(memberName) match {
                  case Some(tableMember) =>
                    SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember).validNel
                  case None              =>
                    QueryMemberNotOnTable(
                      operationShape,
                      memberName,
                      tableName,
                      InvalidQueryTableReference.Kind.Insert
                    ).invalidNel
                }
              }
          case Some(_)                               =>
            primaryKeyMemberNameForOutputType(outputShapeId, primaryKeyTargets) match {
              case Some(primaryKeyMemberName) =>
                List(
                  SqlQueryColumnBuilder
                    .queryColumn(model, tableStructure, table, tableMembersByName(primaryKeyMemberName))).validNel
              case None                       =>
                SqlValidated.invalid(
                  InvalidDeriveInsert(
                    operationShape,
                    s"output type '${outputShapeId.toString}' does not match a primary key target type; ${deriveInsertOutputGuidance(primaryKeyTargets)}"
                  )
                )
            }
        }
      }
    }

    def primaryKeyMemberTargets(tableStructure: StructureShape): List[(String, ShapeId)] =
      tableStructure.getAllMembers.asScala.toList.collect {
        case (memberName, member) if member.sqlPrimaryKey =>
          (memberName, member.getTarget)
      }

    def primaryKeyMemberNameForOutputType(
        outputShapeId: ShapeId,
        primaryKeyTargets: List[(String, ShapeId)]
    ): Option[String] =
      primaryKeyTargets.collectFirst {
        case (memberName, targetShapeId) if targetShapeId == outputShapeId => memberName
      }

    def deriveInsertOutputGuidance(primaryKeyTargets: List[(String, ShapeId)]): String =
      primaryKeyTargets match {
        case (memberName, targetShapeId) :: Nil =>
          s"set output to the primary key target type (${targetShapeId.toString} for member '$memberName') to RETURNING that column, or to a structure whose members name table columns to RETURNING"
        case memberNames                        =>
          val primaryKeyList = memberNames.map(_._1).mkString(", ")
          s"set output to a structure whose members name table columns to RETURNING (this table has ${memberNames.size} primary key members: $primaryKeyList)"
      }

    def extractStructureUpdate(
        model: Model,
        schema: SqlSchema,
        updateStructure: StructureShape,
        tableRef: String
    ): SqlValidated[SqlUpdateQuery] = {
      val queryKind = InvalidQueryTableReference.Kind.Update

      SqlSelectTableContext.resolveTable(model, schema, updateStructure.getId, tableRef, queryKind).andThen {
        case (table, tableStructure) =>
          val tableMembers       = SqlTableMemberCatalog.membersFor(tableStructure)
          val tableMembersByName = tableMembers.map(info => info.memberName -> info).toMap
          val updateMembers      = updateStructure.getAllMembers.asScala.toList
          val updateMemberNames  = updateMembers.map(_._1).toSet
          val primaryKeyMembers  = tableMembers.filter(_.isPrimaryKey)

          (
            validateNoUnknownMembers(updateStructure.getId, table.name, updateMembers, tableMembersByName, queryKind),
            validateNoAutoGeneratedMembers(
              updateStructure.getId,
              table.name,
              updateMembers,
              tableMembersByName,
              queryKind,
              _.databaseManagedOnUpdate
            ),
            validatePrimaryKeysPresent(
              updateStructure.getId,
              table.name,
              primaryKeyMembers,
              updateMemberNames,
              queryKind
            ),
            validateUpdateSetMembers(
              updateStructure.getId,
              table.name,
              model,
              tableStructure,
              table,
              updateMembers,
              tableMembersByName
            )
          ).mapN { (_, _, _, setMembers) =>
            val whereColumns     =
              primaryKeyMembers.map(tableMember =>
                SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember))
            val returningColumns =
              tableMembers
                .filter(_.autoGeneration.contains(SqlUpdatedTimestamp))
                .map(tableMember => SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember))
            SqlUpdateQuery(
              shapeId = updateStructure.getId,
              table = table,
              setColumns = setMembers,
              whereColumns = whereColumns,
              returningColumns = returningColumns
            )
          }
      }
    }

    def validateNoUnknownMembers(
        queryShape: ShapeId,
        tableName: String,
        queryMembers: List[(String, MemberShape)],
        tableMembersByName: Map[String, SqlTableMemberCatalog.TableMemberInfo],
        queryKind: InvalidQueryTableReference.Kind
    ): SqlValidated[Unit] =
      queryMembers
        .traverse { case (memberName, _) =>
          if (tableMembersByName.contains(memberName)) {
            ().validNel
          } else {
            QueryMemberNotOnTable(queryShape, memberName, tableName, queryKind).invalidNel
          }
        }
        .map(_ => ())

    def validateNoAutoGeneratedMembers(
        queryShape: ShapeId,
        tableName: String,
        queryMembers: List[(String, MemberShape)],
        tableMembersByName: Map[String, SqlTableMemberCatalog.TableMemberInfo],
        queryKind: InvalidQueryTableReference.Kind,
        isDatabaseManaged: SqlTableMemberCatalog.TableMemberInfo => Boolean
    ): SqlValidated[Unit] =
      queryMembers
        .traverse { case (memberName, _) =>
          tableMembersByName.get(memberName) match {
            case Some(tableMember) if isDatabaseManaged(tableMember) =>
              QueryIncludesAutoGeneratedMember(queryShape, memberName, tableName, queryKind).invalidNel
            case _                                                   =>
              ().validNel
          }
        }
        .map(_ => ())

    def validatePrimaryKeysPresent(
        queryShape: ShapeId,
        tableName: String,
        primaryKeyMembers: List[SqlTableMemberCatalog.TableMemberInfo],
        updateMemberNames: Set[String],
        queryKind: InvalidQueryTableReference.Kind
    ): SqlValidated[Unit] =
      primaryKeyMembers
        .traverse { tableMember =>
          if (updateMemberNames.contains(tableMember.memberName)) {
            ().validNel
          } else {
            QueryMissingRequiredMember(queryShape, tableMember.memberName, tableName, queryKind).invalidNel
          }
        }
        .map(_ => ())

    def validateUpdateSetMembers(
        queryShape: ShapeId,
        tableName: String,
        model: Model,
        tableStructure: StructureShape,
        table: SqlTable,
        updateMembers: List[(String, MemberShape)],
        tableMembersByName: Map[String, SqlTableMemberCatalog.TableMemberInfo]
    ): SqlValidated[List[SqlQueryColumn]] = {
      val primaryKeyNames =
        tableMembersByName.values.filter(_.isPrimaryKey).map(_.memberName).toSet
      val setMembers      = updateMembers.filterNot { case (memberName, _) =>
        primaryKeyNames.contains(memberName)
      }

      if (setMembers.isEmpty) {
        SqlValidated.invalid(
          QueryMissingRequiredMember(
            queryShape,
            "<non-primary-key member>",
            tableName,
            InvalidQueryTableReference.Kind.Update
          )
        )
      } else {
        setMembers.map { case (memberName, _) =>
          val tableMember = tableMembersByName(memberName)
          SqlQueryColumnBuilder.queryColumn(model, tableStructure, table, tableMember)
        }.validNel
      }
    }
  }
}
