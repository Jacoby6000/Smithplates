package com.jacoby6000.smithy.stache.sql

import cats.syntax.all.*
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.{MemberShape, OperationShape, ShapeId, StructureShape}

/** Catalog of @sqlTable structure members used for query validation. */
private[sql] object SqlTableMemberCatalog {
  final case class TableMemberInfo(
      memberName: String,
      columnName: String,
      required: Boolean,
      autoGeneration: Option[SqlAutoGeneration],
      isPrimaryKey: Boolean
  ) {
    def omittableFromInsert: Boolean =
      autoGeneration.isDefined || (!isPrimaryKey && !required)

    def databaseManagedOnInsert: Boolean =
      autoGeneration.isDefined

    def databaseManagedOnUpdate: Boolean =
      autoGeneration.exists {
        case SqlAutoUuid => false
        case _           => true
      }
  }

  def membersFor(structure: StructureShape): List[TableMemberInfo] =
    com.jacoby6000.smithy.stache.sql.shared.SqlTableMemberOrdering
      .orderedMembers(structure)
      .map { case (memberName, member) =>
      TableMemberInfo(
        memberName = memberName,
        columnName = SmithySqlTraitAccess.columnName(memberName, member),
        required = member.isRequired,
        autoGeneration = SmithySqlTraitAccess.autoGeneration(member),
        isPrimaryKey = SmithySqlTraitAccess.sqlPrimaryKey(member)
      )
    }

  def lookupSqlTableStructure(model: Model, shapeId: ShapeId): Option[StructureShape] =
    for {
      shape <- model.getShape(shapeId).toScala if shape.isStructureShape
      structure = shape.asStructureShape.get()
      if SmithySqlTraitAccess.sqlTableStructure(structure).isDefined
    } yield structure

  def parseShapeId(reference: String): Option[ShapeId] =
    Either.catchOnly[RuntimeException](ShapeId.from(reference)).toOption

  def insertableMembers(tableMembers: List[TableMemberInfo]): List[TableMemberInfo] =
    tableMembers.filterNot(_.databaseManagedOnInsert)

  def updatableSetMembers(tableMembers: List[TableMemberInfo]): List[TableMemberInfo] =
    tableMembers.filter(member => !member.isPrimaryKey && !member.databaseManagedOnUpdate)
}

object SqlQueryExtractor {
  private val UnitShapeId: ShapeId = ShapeId.from("smithy.api#Unit")
  private val BooleanShapeId: ShapeId = ShapeId.from("smithy.api#Boolean")
  val DerivedStructShapeId: ShapeId = ShapeId.from("stache.codegen.sql#DerivedStruct")

  def extract(model: Model, schema: SqlSchema): SqlValidated[SqlQueries] =
    (
      extractInserts(model, schema),
      extractUpdates(model, schema),
      extractDeletes(model, schema),
      extractSelectOnes(model, schema),
      SqlDeriveSelectExtractor.extractDeriveSelects(model, schema)
    ).mapN(SqlQueries(_, _, _, _, _))

  private def extractInserts(model: Model, schema: SqlSchema): SqlValidated[List[SqlInsertQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        SmithySqlTraitAccess.sqlDeriveInsert(operation).map(insertTrait => (operation, insertTrait))
      }
      .traverse { case (operation, insertTrait) =>
        extractInsert(model, schema, operation, insertTrait.getTargetTable)
      }

  private def extractUpdates(model: Model, schema: SqlSchema): SqlValidated[List[SqlUpdateQuery]] =
    (
      extractStructureUpdates(model, schema),
      extractDeriveUpdates(model, schema)
    ).mapN(_ ++ _)

  private def extractStructureUpdates(model: Model, schema: SqlSchema): SqlValidated[List[SqlUpdateQuery]] =
    model.getStructureShapes.asScala.toList
      .flatMap { structure =>
        SmithySqlTraitAccess.sqlUpdate(structure).map(updateTrait => (structure, updateTrait))
      }
      .traverse { case (structure, updateTrait) =>
        extractStructureUpdate(model, schema, structure, updateTrait.getTableRef)
      }

  private def extractDeriveUpdates(model: Model, schema: SqlSchema): SqlValidated[List[SqlUpdateQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        SmithySqlTraitAccess.sqlDeriveUpdate(operation).map(updateTrait => (operation, updateTrait))
      }
      .traverse { case (operation, updateTrait) =>
        extractDeriveUpdate(model, schema, operation, updateTrait.getTargetTable)
      }

  private def extractDeletes(model: Model, schema: SqlSchema): SqlValidated[List[SqlDeleteQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        SmithySqlTraitAccess.sqlDeriveDelete(operation).map(deleteTrait => (operation, deleteTrait))
      }
      .traverse { case (operation, deleteTrait) =>
        extractDeriveDelete(model, schema, operation, deleteTrait.getTargetTable)
      }

  private def extractSelectOnes(model: Model, schema: SqlSchema): SqlValidated[List[SqlSelectOneQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        SmithySqlTraitAccess.sqlDeriveSelectOne(operation).map(selectTrait => (operation, selectTrait))
      }
      .traverse { case (operation, selectTrait) =>
        extractDeriveSelectOne(model, schema, operation, selectTrait.getTargetTable)
      }

  private def extractInsert(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      targetTable: String
  ): SqlValidated[SqlInsertQuery] = {
    val queryKind = InvalidQueryTableReference.Kind.Insert
    val operationShape = operation.getId

    resolveTable(model, schema, operationShape, targetTable, queryKind).andThen {
      case (table, tableStructure) =>
        requireDerivedStructInput(operation, "sqlDeriveInsert").andThen { _ =>
          val tableMembers = SqlTableMemberCatalog.membersFor(tableStructure)
          val tableMembersByName = tableMembers.map(info => info.memberName -> info).toMap
          val insertableMembers = SqlTableMemberCatalog.insertableMembers(tableMembers)

          resolveInsertReturningColumns(
            model,
            operation,
            tableStructure,
            tableMembersByName,
            table.name
          ).map { returningColumns =>
            val columns = insertableMembers.map { tableMember =>
              SqlQueryColumn(tableMember.memberName, tableMember.columnName)
            }
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

  private def requireDerivedStructInput(operation: OperationShape, deriveTrait: String): SqlValidated[Unit] = {
    val operationShape = operation.getId
    val inputShapeId = Option(operation.getInputShape).getOrElse(UnitShapeId)

    if (inputShapeId == DerivedStructShapeId) {
      ().validNel
    } else {
      deriveTrait match {
        case "sqlDeriveInsert" =>
          InvalidDeriveInsert(
            operationShape,
            s"input must be ${DerivedStructShapeId.toString}; codegen expands table-derived members from the @sqlDeriveInsert targetTable"
          ).invalidNel
        case "sqlDeriveUpdate" =>
          InvalidDeriveUpdate(
            operationShape,
            s"input must be ${DerivedStructShapeId.toString}; codegen expands whereClause and updateFields from the @sqlDeriveUpdate targetTable"
          ).invalidNel
        case "sqlDeriveDelete" =>
          InvalidDeriveDelete(
            operationShape,
            s"input must be ${DerivedStructShapeId.toString}; codegen expands whereClause from the @sqlDeriveDelete targetTable"
          ).invalidNel
        case "sqlDeriveSelectOne" =>
          InvalidDeriveSelectOne(
            operationShape,
            s"input must be ${DerivedStructShapeId.toString}; codegen expands whereClause from the @sqlDeriveSelectOne targetTable"
          ).invalidNel
        case other =>
          InvalidPluginConfig(s"unknown derive trait '$other' in requireDerivedStructInput").invalidNel
      }
    }
  }

  private def requireBooleanDeriveUpdateOutput(operation: OperationShape): SqlValidated[Unit] =
    requireBooleanDeriveOutput(
      operation,
      error =>
        InvalidDeriveUpdate(
          operation.getId,
          s"output must be Boolean (false when no row was updated); got '${error.outputShapeId.toString}'"
        )
    )

  private def requireBooleanDeriveDeleteOutput(operation: OperationShape): SqlValidated[Unit] =
    requireBooleanDeriveOutput(
      operation,
      error =>
        InvalidDeriveDelete(
          operation.getId,
          s"output must be Boolean (false when no row was deleted); got '${error.outputShapeId.toString}'"
        )
    )

  private final case class UnexpectedDeriveOutputShape(outputShapeId: ShapeId)

  private def requireBooleanDeriveOutput(
      operation: OperationShape,
      toError: UnexpectedDeriveOutputShape => SqlSchemaError
  ): SqlValidated[Unit] = {
    val outputShapeId = operation.getOutput.toScala.getOrElse(operation.getOutputShape)

    if (outputShapeId == BooleanShapeId) {
      ().validNel
    } else {
      toError(UnexpectedDeriveOutputShape(outputShapeId)).invalidNel
    }
  }

  private def extractDeriveUpdate(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      targetTable: String
  ): SqlValidated[SqlUpdateQuery] = {
    val queryKind = InvalidQueryTableReference.Kind.DeriveUpdate
    val operationShape = operation.getId

    resolveTable(model, schema, operationShape, targetTable, queryKind).andThen {
      case (table, tableStructure) =>
        (
          requireDerivedStructInput(operation, "sqlDeriveUpdate"),
          requireBooleanDeriveUpdateOutput(operation),
          validateDeriveUpdateColumns(operationShape, table.name, tableStructure)
        ).mapN { (_, _, columns) =>
          val autoUpdatedColumns =
            SqlTableMemberCatalog
              .membersFor(tableStructure)
              .filter(_.autoGeneration.contains(SqlUpdatedTimestamp))
              .map(_.columnName)
          SqlUpdateQuery(
            shapeId = operationShape,
            table = table,
            setColumns = columns.setColumns,
            whereColumns = columns.whereColumns,
            returningColumns = autoUpdatedColumns
          )
        }
    }
  }

  private def extractDeriveDelete(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      targetTable: String
  ): SqlValidated[SqlDeleteQuery] = {
    val queryKind = InvalidQueryTableReference.Kind.DeriveDelete
    val operationShape = operation.getId

    resolveTable(model, schema, operationShape, targetTable, queryKind).andThen {
      case (table, tableStructure) =>
        (
          requireDerivedStructInput(operation, "sqlDeriveDelete"),
          requireBooleanDeriveDeleteOutput(operation),
          validateDeriveDeleteColumns(operationShape, table.name, tableStructure)
        ).mapN { (_, _, whereColumns) =>
          SqlDeleteQuery(
            shapeId = operationShape,
            table = table,
            whereColumns = whereColumns,
            returningColumns = whereColumns.map(_.columnName)
          )
        }
    }
  }

  private def validateDeriveDeleteColumns(
      operationShape: ShapeId,
      tableName: String,
      tableStructure: StructureShape
  ): SqlValidated[List[SqlQueryColumn]] =
    validateDerivePrimaryKeyColumns(operationShape, tableName, tableStructure, InvalidDeriveDelete(_, _))

  private def extractDeriveSelectOne(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      targetTable: String
  ): SqlValidated[SqlSelectOneQuery] = {
    val queryKind = InvalidQueryTableReference.Kind.DeriveSelectOne
    val operationShape = operation.getId

    resolveTable(model, schema, operationShape, targetTable, queryKind).andThen {
      case (table, tableStructure) =>
        (
          requireDerivedStructInput(operation, "sqlDeriveSelectOne"),
          requireTableStructureOutput(operation, tableStructure.getId),
          validateDerivePrimaryKeyColumns(
            operationShape,
            table.name,
            tableStructure,
            InvalidDeriveSelectOne(_, _)
          )
        ).mapN { (_, _, whereColumns) =>
          val selectColumns =
            SqlTableMemberCatalog.membersFor(tableStructure).map { tableMember =>
              SqlQueryColumn(tableMember.memberName, tableMember.columnName)
            }
          SqlSelectOneQuery(
            shapeId = operationShape,
            table = table,
            selectColumns = selectColumns,
            whereColumns = whereColumns
          )
        }
    }
  }

  private def requireTableStructureOutput(operation: OperationShape, tableShapeId: ShapeId): SqlValidated[Unit] = {
    val operationShape = operation.getId
    val outputShapeId = operation.getOutput.toScala.getOrElse(operation.getOutputShape)

    if (outputShapeId == tableShapeId) {
      ().validNel
    } else {
      InvalidDeriveSelectOne(
        operationShape,
        s"output must be the target @sqlTable structure '${tableShapeId.toString}'; got '${outputShapeId.toString}'"
      ).invalidNel
    }
  }

  private def validateDerivePrimaryKeyColumns(
      operationShape: ShapeId,
      tableName: String,
      tableStructure: StructureShape,
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
        .map { tableMember =>
          SqlQueryColumn(tableMember.memberName, tableMember.columnName)
        }
        .validNel
    }
  }

  private final case class DeriveUpdateColumns(
      whereColumns: List[SqlQueryColumn],
      setColumns: List[SqlQueryColumn]
  )

  private def validateDeriveUpdateColumns(
      operationShape: ShapeId,
      tableName: String,
      tableStructure: StructureShape
  ): SqlValidated[DeriveUpdateColumns] = {
    val tableMembers = SqlTableMemberCatalog.membersFor(tableStructure)
    val primaryKeyMembers = tableMembers.filter(_.isPrimaryKey)
    val setMembers = SqlTableMemberCatalog.updatableSetMembers(tableMembers)

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
        whereColumns = primaryKeyMembers.map { tableMember =>
          SqlQueryColumn(tableMember.memberName, tableMember.columnName)
        },
        setColumns = setMembers.map { tableMember =>
          SqlQueryColumn(tableMember.memberName, tableMember.columnName)
        }
      ).validNel
    }
  }

  private def resolveInsertReturningColumns(
      model: Model,
      operation: OperationShape,
      tableStructure: StructureShape,
      tableMembersByName: Map[String, SqlTableMemberCatalog.TableMemberInfo],
      tableName: String
  ): SqlValidated[List[String]] = {
    val operationShape = operation.getId
    val outputShapeId = operation.getOutput.toScala.getOrElse(operation.getOutputShape)
    val primaryKeyTargets = primaryKeyMemberTargets(tableStructure)

    if (outputShapeId == UnitShapeId) {
      SqlValidated.invalid(
        InvalidDeriveInsert(operationShape, deriveInsertOutputGuidance(primaryKeyTargets))
      )
    } else {
      model.getShape(outputShapeId).toScala match {
        case None =>
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
                case Some(tableMember) => tableMember.columnName.validNel
                case None =>
                  QueryMemberNotOnTable(
                    operationShape,
                    memberName,
                    tableName,
                    InvalidQueryTableReference.Kind.Insert
                  ).invalidNel
              }
            }
        case Some(_) =>
          primaryKeyMemberNameForOutputType(outputShapeId, primaryKeyTargets) match {
            case Some(primaryKeyMemberName) =>
              List(tableMembersByName(primaryKeyMemberName).columnName).validNel
            case None =>
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

  private def primaryKeyMemberTargets(tableStructure: StructureShape): List[(String, ShapeId)] =
    tableStructure.getAllMembers.asScala.toList.collect {
      case (memberName, member) if SmithySqlTraitAccess.sqlPrimaryKey(member) =>
        (memberName, member.getTarget)
    }

  private def primaryKeyMemberNameForOutputType(
      outputShapeId: ShapeId,
      primaryKeyTargets: List[(String, ShapeId)]
  ): Option[String] =
    primaryKeyTargets.collectFirst {
      case (memberName, targetShapeId) if targetShapeId == outputShapeId => memberName
    }

  private def deriveInsertOutputGuidance(primaryKeyTargets: List[(String, ShapeId)]): String =
    primaryKeyTargets match {
      case (memberName, targetShapeId) :: Nil =>
        s"set output to the primary key target type (${targetShapeId.toString} for member '$memberName') to RETURNING that column, or to a structure whose members name table columns to RETURNING"
      case memberNames =>
        val primaryKeyList = memberNames.map(_._1).mkString(", ")
        s"set output to a structure whose members name table columns to RETURNING (this table has ${memberNames.size} primary key members: $primaryKeyList)"
    }

  private def extractStructureUpdate(
      model: Model,
      schema: SqlSchema,
      updateStructure: StructureShape,
      tableRef: String
  ): SqlValidated[SqlUpdateQuery] = {
    val queryKind = InvalidQueryTableReference.Kind.Update

    resolveTable(model, schema, updateStructure.getId, tableRef, queryKind).andThen {
      case (table, tableStructure) =>
        val tableMembers = SqlTableMemberCatalog.membersFor(tableStructure)
        val tableMembersByName = tableMembers.map(info => info.memberName -> info).toMap
        val updateMembers = updateStructure.getAllMembers.asScala.toList
        val updateMemberNames = updateMembers.map(_._1).toSet
        val primaryKeyMembers = tableMembers.filter(_.isPrimaryKey)

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
            updateMembers,
            tableMembersByName
          )
        ).mapN { (_, _, _, setMembers) =>
          val whereColumns = primaryKeyMembers.map { tableMember =>
            SqlQueryColumn(tableMember.memberName, tableMember.columnName)
          }
          val autoUpdatedColumns =
            tableMembers.filter(_.autoGeneration.contains(SqlUpdatedTimestamp)).map(_.columnName)
          SqlUpdateQuery(
            shapeId = updateStructure.getId,
            table = table,
            setColumns = setMembers,
            whereColumns = whereColumns,
            returningColumns = autoUpdatedColumns
          )
        }
    }
  }

  private def resolveTable(
      model: Model,
      schema: SqlSchema,
      queryShape: ShapeId,
      tableRef: String,
      queryKind: InvalidQueryTableReference.Kind
  ): SqlValidated[(SqlTable, StructureShape)] =
    SqlTableMemberCatalog
      .parseShapeId(tableRef)
      .flatMap(SqlTableMemberCatalog.lookupSqlTableStructure(model, _))
      .flatMap { tableStructure =>
        schema.tables.find(_.shapeId == tableStructure.getId).map(table => (table, tableStructure))
      }
      .map(SqlValidated.valid)
      .getOrElse(SqlValidated.invalid(InvalidQueryTableReference(queryShape, tableRef, queryKind)))

  private def validateNoUnknownMembers(
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

  private def validateNoAutoGeneratedMembers(
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
          case _ =>
            ().validNel
        }
      }
      .map(_ => ())

  private def validatePrimaryKeysPresent(
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

  private def validateUpdateSetMembers(
      queryShape: ShapeId,
      tableName: String,
      updateMembers: List[(String, MemberShape)],
      tableMembersByName: Map[String, SqlTableMemberCatalog.TableMemberInfo]
  ): SqlValidated[List[SqlQueryColumn]] = {
    val primaryKeyNames =
      tableMembersByName.values.filter(_.isPrimaryKey).map(_.memberName).toSet
    val setMembers = updateMembers.filterNot { case (memberName, _) =>
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
      setMembers
        .map { case (memberName, _) =>
          val tableMember = tableMembersByName(memberName)
          SqlQueryColumn(memberName, tableMember.columnName)
        }
        .validNel
    }
  }
}
