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
  private val UnitShapeId: ShapeId    = ShapeId.from("smithy.api#Unit")
  private val BooleanShapeId: ShapeId = ShapeId.from("smithy.api#Boolean")
  val DerivedStructShapeId: ShapeId   = ShapeId.from("smithplates.codegen.sql#DerivedStruct")

  private def queryColumn(
      model: Model,
      tableStructure: StructureShape,
      table: SqlTable,
      tableMember: SqlTableMemberCatalog.TableMemberInfo
  ): SqlQueryColumn = {
    val member       = tableStructure.getMember(tableMember.memberName).get()
    val memberType   = SqlIrTypeNameResolver.resolveMember(model, tableMember.memberName, member)
    val tableColumn  = table.columns.find(_.name == tableMember.columnName)
    val jsonTypeName =
      table.columns
        .find(_.name == tableMember.columnName)
        .collect { case column if column.columnType == SqlColumnType.Json => memberType.typeName }
    SqlQueryColumn(
      memberName = tableMember.memberName,
      columnName = tableMember.columnName,
      typeName = memberType.typeName,
      nullable = tableColumn.exists(_.nullable),
      jsonTypeName = jsonTypeName,
      isStructure = memberType.isStructure,
      structureShapeId = memberType.structureShapeId
    )
  }

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
        operation.sqlDeriveInsert.map(insertTrait => (operation, insertTrait))
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
        structure.sqlUpdate.map(updateTrait => (structure, updateTrait))
      }
      .traverse { case (structure, updateTrait) =>
        extractStructureUpdate(model, schema, structure, updateTrait.getTableRef)
      }

  private def extractDeriveUpdates(model: Model, schema: SqlSchema): SqlValidated[List[SqlUpdateQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        operation.sqlDeriveUpdate.map(updateTrait => (operation, updateTrait))
      }
      .traverse { case (operation, updateTrait) =>
        extractDeriveUpdate(model, schema, operation, updateTrait.getTargetTable)
      }

  private def extractDeletes(model: Model, schema: SqlSchema): SqlValidated[List[SqlDeleteQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        operation.sqlDeriveDelete.map(deleteTrait => (operation, deleteTrait))
      }
      .traverse { case (operation, deleteTrait) =>
        extractDeriveDelete(model, schema, operation, deleteTrait.getTargetTable)
      }

  private def extractSelectOnes(model: Model, schema: SqlSchema): SqlValidated[List[SqlSelectOneQuery]] =
    model.getOperationShapes.asScala.toList
      .flatMap { operation =>
        operation.sqlDeriveSelectOne.map(selectTrait => (operation, selectTrait))
      }
      .traverse { case (operation, selectTrait) =>
        SqlDeriveSelectOneExtractor.extract(model, schema, operation, selectTrait)
      }

  private def extractInsert(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      targetTable: String
  ): SqlValidated[SqlInsertQuery] = {
    val queryKind      = InvalidQueryTableReference.Kind.Insert
    val operationShape = operation.getId

    resolveTable(model, schema, operationShape, targetTable, queryKind).andThen { case (table, tableStructure) =>
      requireDerivedStructInput(operation, "sqlDeriveInsert").andThen { _ =>
        rejectRequiredForeignKeyCycle(schema, operationShape, table).andThen { _ =>
          val tableMembers      = SqlTableMemberCatalog.membersFor(tableStructure)
          val insertableMembers = SqlTableMemberCatalog.insertableMembers(tableMembers)

          resolveInsertReturningColumns(model, operation, tableStructure, table).map { returningColumns =>
            val columns = insertableMembers.map(tableMember => queryColumn(model, tableStructure, table, tableMember))
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

  private def rejectRequiredForeignKeyCycle(
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

  private def requireDerivedStructInput(operation: OperationShape, deriveTrait: String): SqlValidated[Unit] = {
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

  private val BooleanMutationOutputGuidance: String =
    "output must be a structure with exactly one @required member targeting smithy.api#Boolean"

  private val DmlDerivedStructOutputGuidance: String =
    s"output must be ${DerivedStructShapeId.toString}; codegen expands primary key and auto-generated table members from RETURNING"

  private def derivePkAndAutoGeneratedReturningColumns(
      model: Model,
      tableStructure: StructureShape,
      table: SqlTable
  ): List[SqlQueryColumn] =
    SqlTableMemberCatalog
      .membersFor(tableStructure)
      .filter(member => member.isPrimaryKey || member.autoGeneration.isDefined)
      .map(member => queryColumn(model, tableStructure, table, member))

  private def requireDerivedStructDmlOutput(
      operation: OperationShape,
      deriveTrait: String
  ): SqlValidated[Unit] = {
    val operationShape = operation.getId
    val outputShapeId  = operation.getOutput.toScala.getOrElse(operation.getOutputShape)

    if (outputShapeId == DerivedStructShapeId) {
      ().validNel
    } else if (outputShapeId == UnitShapeId) {
      deriveTrait match {
        case "sqlDeriveInsert" =>
          InvalidDeriveInsert(operationShape, s"$DmlDerivedStructOutputGuidance; got Unit").invalidNel
        case "sqlDeriveUpdate" =>
          InvalidDeriveUpdate(operationShape, s"$DmlDerivedStructOutputGuidance; got Unit").invalidNel
        case other             =>
          InvalidPluginConfig(s"unknown derive trait '$other' in requireDerivedStructDmlOutput").invalidNel
      }
    } else {
      deriveTrait match {
        case "sqlDeriveInsert" =>
          InvalidDeriveInsert(
            operationShape,
            s"$DmlDerivedStructOutputGuidance; got '${outputShapeId.toString}'").invalidNel
        case "sqlDeriveUpdate" =>
          InvalidDeriveUpdate(
            operationShape,
            s"$DmlDerivedStructOutputGuidance; got '${outputShapeId.toString}'").invalidNel
        case other             =>
          InvalidPluginConfig(s"unknown derive trait '$other' in requireDerivedStructDmlOutput").invalidNel
      }
    }
  }

  private def requireBooleanMutationDeriveDeleteOutput(
      model: Model,
      operation: OperationShape
  ): SqlValidated[Unit] =
    requireBooleanMutationDeriveOutput(
      model,
      operation,
      (operationShape, reason) => InvalidDeriveDelete(operationShape, reason)
    )

  private def requireBooleanMutationDeriveOutput(
      model: Model,
      operation: OperationShape,
      toError: (ShapeId, String) => SqlSchemaError
  ): SqlValidated[Unit] = {
    val operationShape = operation.getId
    val outputShapeId  = operation.getOutput.toScala.getOrElse(operation.getOutputShape)

    if (outputShapeId == BooleanShapeId) {
      toError(operationShape, s"$BooleanMutationOutputGuidance; got bare Boolean").invalidNel
    } else if (outputShapeId == DerivedStructShapeId) {
      toError(operationShape, s"$BooleanMutationOutputGuidance; got ${DerivedStructShapeId.toString}").invalidNel
    } else {
      model.getShape(outputShapeId).toScala match {
        case None                                  =>
          toError(
            operationShape,
            s"output shape '${outputShapeId.toString}' is not defined in the model"
          ).invalidNel
        case Some(shape) if shape.isStructureShape =>
          val members                = shape.asStructureShape.get().getAllMembers.asScala.toList
          val requiredBooleanMembers =
            members.filter { case (_, member) =>
              member.isRequired && member.getTarget == BooleanShapeId
            }
          if (members.size == 1 && requiredBooleanMembers.size == 1) {
            ().validNel
          } else if (members.isEmpty) {
            toError(operationShape, s"$BooleanMutationOutputGuidance; output structure has no members").invalidNel
          } else if (requiredBooleanMembers.isEmpty) {
            toError(
              operationShape,
              s"$BooleanMutationOutputGuidance; no @required member targets Boolean"
            ).invalidNel
          } else if (requiredBooleanMembers.size > 1) {
            toError(
              operationShape,
              s"$BooleanMutationOutputGuidance; output structure has ${requiredBooleanMembers.size} @required Boolean members"
            ).invalidNel
          } else if (members.size > 1) {
            toError(
              operationShape,
              s"$BooleanMutationOutputGuidance; output structure has ${members.size} members but exactly one @required Boolean member is allowed"
            ).invalidNel
          } else {
            toError(
              operationShape,
              s"$BooleanMutationOutputGuidance; Boolean member '${requiredBooleanMembers.head._1}' is not @required"
            ).invalidNel
          }
        case Some(_)                               =>
          toError(operationShape, s"$BooleanMutationOutputGuidance; got '${outputShapeId.toString}'").invalidNel
      }
    }
  }

  private def extractDeriveUpdate(
      model: Model,
      schema: SqlSchema,
      operation: OperationShape,
      targetTable: String
  ): SqlValidated[SqlUpdateQuery] = {
    val queryKind      = InvalidQueryTableReference.Kind.DeriveUpdate
    val operationShape = operation.getId

    resolveTable(model, schema, operationShape, targetTable, queryKind).andThen { case (table, tableStructure) =>
      (
        requireDerivedStructInput(operation, "sqlDeriveUpdate"),
        requireDerivedStructDmlOutput(operation, "sqlDeriveUpdate"),
        validateDeriveUpdateColumns(
          operationShape,
          table.name,
          model,
          tableStructure,
          table
        )
      ).mapN { (_, _, columns) =>
        SqlUpdateQuery(
          shapeId = operationShape,
          table = table,
          setColumns = columns.setColumns,
          whereColumns = columns.whereColumns,
          returningColumns = derivePkAndAutoGeneratedReturningColumns(model, tableStructure, table)
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
    val queryKind      = InvalidQueryTableReference.Kind.DeriveDelete
    val operationShape = operation.getId

    resolveTable(model, schema, operationShape, targetTable, queryKind).andThen { case (table, tableStructure) =>
      (
        requireDerivedStructInput(operation, "sqlDeriveDelete"),
        requireBooleanMutationDeriveDeleteOutput(model, operation),
        validateDeriveDeleteColumns(operationShape, table.name, model, tableStructure, table)
      ).mapN { (_, _, whereColumns) =>
        SqlDeleteQuery(
          shapeId = operationShape,
          table = table,
          whereColumns = whereColumns,
          returningColumns = whereColumns
        )
      }
    }
  }

  private def validateDeriveDeleteColumns(
      operationShape: ShapeId,
      tableName: String,
      model: Model,
      tableStructure: StructureShape,
      table: SqlTable
  ): SqlValidated[List[SqlQueryColumn]] =
    validateDerivePrimaryKeyColumns(operationShape, tableName, model, tableStructure, table, InvalidDeriveDelete(_, _))

  private def validateDerivePrimaryKeyColumns(
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
        .map(tableMember => queryColumn(model, tableStructure, table, tableMember))
        .validNel
    }
  }

  private[service] def deriveSelectOnePrimaryKeyColumns(
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
      InvalidDeriveSelectOne(_, _)
    )

  final private case class DeriveUpdateColumns(
      whereColumns: List[SqlQueryColumn],
      setColumns: List[SqlQueryColumn]
  )

  private def validateDeriveUpdateColumns(
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
        whereColumns = primaryKeyMembers.map(tableMember => queryColumn(model, tableStructure, table, tableMember)),
        setColumns = setMembers.map(tableMember => queryColumn(model, tableStructure, table, tableMember))
      ).validNel
    }
  }

  private def resolveInsertReturningColumns(
      model: Model,
      operation: OperationShape,
      tableStructure: StructureShape,
      table: SqlTable
  ): SqlValidated[List[SqlQueryColumn]] =
    requireDerivedStructDmlOutput(operation, "sqlDeriveInsert").map { _ =>
      derivePkAndAutoGeneratedReturningColumns(model, tableStructure, table)
    }

  private def extractStructureUpdate(
      model: Model,
      schema: SqlSchema,
      updateStructure: StructureShape,
      tableRef: String
  ): SqlValidated[SqlUpdateQuery] = {
    val queryKind = InvalidQueryTableReference.Kind.Update

    resolveTable(model, schema, updateStructure.getId, tableRef, queryKind).andThen { case (table, tableStructure) =>
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
          primaryKeyMembers.map(tableMember => queryColumn(model, tableStructure, table, tableMember))
        val returningColumns =
          tableMembers
            .filter(_.autoGeneration.contains(SqlUpdatedTimestamp))
            .map(tableMember => queryColumn(model, tableStructure, table, tableMember))
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
          case _                                                   =>
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
        queryColumn(model, tableStructure, table, tableMember)
      }.validNel
    }
  }
}
