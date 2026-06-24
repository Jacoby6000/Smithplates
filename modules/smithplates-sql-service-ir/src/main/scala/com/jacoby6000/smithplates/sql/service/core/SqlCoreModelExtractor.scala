package com.jacoby6000.smithplates.sql.service.core

import cats.data.Validated
import cats.kernel.instances.order.catsKernelOrderingForOrder
import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.invalidNel
import com.jacoby6000.smithplates.codegen.core.EnumValue
import com.jacoby6000.smithplates.codegen.core.Field
import com.jacoby6000.smithplates.codegen.core.InvalidSmithyShape
import com.jacoby6000.smithplates.codegen.core.Model as CodegenModel
import com.jacoby6000.smithplates.codegen.core.ModelExtractor
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelMeta
import com.jacoby6000.smithplates.codegen.core.ModelMetaValidator
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.ModelSetValidator
import com.jacoby6000.smithplates.codegen.core.ModelValidator
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.OperationMeta
import com.jacoby6000.smithplates.codegen.core.OperationMetaValidator
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.OperationValidator
import com.jacoby6000.smithplates.codegen.core.PrimitiveLiteral
import com.jacoby6000.smithplates.codegen.core.ServiceMeta
import com.jacoby6000.smithplates.codegen.core.ServiceMetaValidator
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.ServiceModelValidator
import com.jacoby6000.smithplates.codegen.core.SystemValidator
import com.jacoby6000.smithplates.codegen.core.TimestampFormat as CoreTimestampFormat
import com.jacoby6000.smithplates.codegen.core.Variant
import com.jacoby6000.smithplates.smithy.neutral.*
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlOperation
import com.jacoby6000.smithplates.sql.service.SqlQueryExtractor
import com.jacoby6000.smithplates.sql.service.SqlService
import com.jacoby6000.smithplates.sql.service.SqlServiceExtractor
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.traits.TagsTrait

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object SqlCoreModelExtractor extends SmithyModelExtractor[SqlMeta, SqlServiceMeta, SqlOperationMeta] {
  given ModelMetaValidator[SqlMeta]                                = ModelMetaValidator.noop
  given OperationMetaValidator[SqlOperationMeta]                   = OperationMetaValidator.noop
  given ServiceMetaValidator[SqlServiceMeta]                       = ServiceMetaValidator.noop
  given ModelValidator[SqlMeta]                                    = ModelValidator.default
  given OperationValidator[SqlOperationMeta]                       = OperationValidator.default
  given ServiceModelValidator[SqlServiceMeta, SqlOperationMeta]    = ServiceModelValidator.default
  given ModelSetValidator[SqlMeta]                                 = ModelSetValidator.default
  given SystemValidator[SqlMeta, SqlServiceMeta, SqlOperationMeta] = SystemValidator.default

  def extract(
      model: SmithyModel
  ): CodegenValidated[(ModelSet[SqlMeta], List[ServiceModel[SqlServiceMeta, SqlOperationMeta]])] =
    extractValidated(model)(using SystemValidator.default)

  def extractAndValidate(
      model: SmithyModel
  ): CodegenValidated[(ModelSet[SqlMeta], List[ServiceModel[SqlServiceMeta, SqlOperationMeta]])] =
    extract(model)

  override def extractValidated(model: SmithyModel)(using
      validator: SystemValidator[SqlMeta, SqlServiceMeta, SqlOperationMeta]
  ): CodegenValidated[(ModelSet[SqlMeta], List[ServiceModel[SqlServiceMeta, SqlOperationMeta]])] =
    internal
      .extractFromSmithy(model)
      .andThen { case (modelSet, services) =>
        ModelExtractor.validateServices(modelSet, services)(using validator).map(_ => (modelSet, services))
      }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def extractFromSmithy(
        model: SmithyModel
    ): CodegenValidated[(ModelSet[SqlMeta], List[ServiceModel[SqlServiceMeta, SqlOperationMeta]])] =
      SqlIrExtractor
        .extract(model)
        .leftMap(_.map(error => InvalidSmithyShape(ModelId("smithy", "sql"), error.message)))
        .andThen { schema =>
          SqlServiceExtractor
            .extract(model)
            .leftMap(_.map(error => InvalidSmithyShape(ModelId("smithy", "sql"), error.message)))
            .andThen { services =>
              services
                .traverse(service => convertService(model, schema, service))
                .map { results =>
                  val serviceModels = results.map(_._1)
                  val models        = results.flatMap(_._2).distinct.sortBy(_.id)
                  (ModelSet(models), serviceModels)
                }
            }
        }

    def convertService(
        model: SmithyModel,
        schema: SqlSchema,
        sqlService: SqlService
    ): CodegenValidated[(ServiceModel[SqlServiceMeta, SqlOperationMeta], List[CodegenModel[SqlMeta]])] = {
      val serviceId  = ModelIds.fromShapeId(sqlService.shapeId)
      val operations =
        sqlService.operations.map { operation =>
          OperationModel(
            id = ModelIds.fromShapeId(operation.shapeId),
            meta = OperationMeta(
              documentation = None,
              tags = Nil,
              feature = SqlOperationMeta()
            ),
            input = internal.operationInputRef(operation),
            output = internal.operationOutputRef(operation),
            errors = operation.errorShapes.map(shapeId => ModelRef(ModelIds.fromShapeId(shapeId)))
          )
        }

      val service = ServiceModel(
        id = serviceId,
        meta = ServiceMeta(
          documentation = None,
          tags = Nil,
          feature = SqlServiceMeta()
        ),
        operations = operations
      )

      val rootShapeIds =
        sqlService.operations
          .flatMap { operation =>
            List(operation.inputShape) ++ operation.outputShape.toList ++ operation.errorShapes
          }
          .filterNot(id => id == SmithyPrelude.UnitShapeId || id == SqlQueryExtractor.DerivedStructShapeId)
          .distinct

      val allRootIds = (rootShapeIds ++ schema.tables.map(_.shapeId)).distinct

      val (structureIds, unionIds) = SqlShapeGraph.referencedShapes(model, allRootIds)
      val aliasIds                 = SmithyAliasClosure.aliasShapeIds(model, structureIds ++ unionIds)

      (
        aliasIds.traverse(shapeId => extractAlias(model, shapeId)),
        structureIds.traverse(shapeId => extractStructure(model, schema, shapeId)),
        unionIds.traverse(shapeId => extractUnion(model, shapeId))
      ).mapN { case (aliases, structures, unions) =>
        SqlShapeIrExtractor.extractDiscovered(model, structureIds, unionIds) match {
          case cats.data.Validated.Invalid(errors) =>
            Validated.invalid(errors.map(error => InvalidSmithyShape(ModelId("smithy", "sql"), error.message)))
          case cats.data.Validated.Valid(shapeIr)  =>
            val (stringEnums, intEnums) =
              SqlEnumExtractor.extractReferenced(
                model = model,
                namespace = sqlService.shapeId.getNamespace,
                structures = shapeIr.structures,
                unions = shapeIr.unions
              )
            (
              stringEnums.traverse(enumShape => extractStringEnum(model, enumShape.shapeId)),
              intEnums.traverse(enumShape => extractIntEnum(model, enumShape.shapeId))
            ).mapN { (stringEnumModels, intEnumModels) =>
              (
                service,
                aliases ++ structures ++ unions ++ stringEnumModels ++ intEnumModels
              )
            }
        }
      }.andThen(identity)
    }

    def operationInputRef(operation: SqlOperation): Option[ModelRef] =
      if (operation.inputShape == SmithyPrelude.UnitShapeId ||
        operation.inputShape == SqlQueryExtractor.DerivedStructShapeId) {
        None
      } else {
        Some(ModelRef(ModelIds.fromShapeId(operation.inputShape)))
      }

    def operationOutputRef(operation: SqlOperation): Option[ModelRef] =
      operation.outputShape
        .filter(_ != SmithyPrelude.UnitShapeId)
        .filter(_ != SqlQueryExtractor.DerivedStructShapeId)
        .map(shapeId => ModelRef(ModelIds.fromShapeId(shapeId)))

    def extractStructure(
        model: SmithyModel,
        schema: SqlSchema,
        shapeId: ShapeId
    ): CodegenValidated[CodegenModel.Structure[SqlMeta]] =
      model.getShape(shapeId).toScala.flatMap(_.asStructureShape.toScala) match {
        case None            =>
          InvalidSmithyShape(ModelIds.fromShapeId(shapeId), "expected a structure shape").invalidNel
        case Some(structure) =>
          val isSqlTable = schema.tables.exists(_.shapeId == structure.getId)
          structure.getAllMembers.asScala.toList
            .traverse { case (memberName, member) =>
              SmithyNeutralTypeResolver
                .resolveMemberType(
                  model,
                  member,
                  memberShape => SqlShapeIrExtractor.internal.memberOptional(memberShape, isSqlTable),
                  (_, memberShape) => sqlTimestampFormat(model, structure.getId.getName, memberName, memberShape),
                  SmithyNeutralTypeResolver.MemberContext(
                    shapeId = structure.getId,
                    memberName = memberName,
                    role = "structure"
                  )
                )
                .map(Field(memberName, _))
            }
            .map { fields =>
              CodegenModel.Structure(
                id = ModelIds.fromShapeId(shapeId),
                meta = modelMeta(model, shapeId, tableMetaForShape(schema, shapeId)),
                fields = fields
              )
            }
      }

    def extractUnion(
        model: SmithyModel,
        shapeId: ShapeId
    ): CodegenValidated[CodegenModel.Union[SqlMeta]] =
      model.getShape(shapeId).toScala.flatMap(_.asUnionShape.toScala) match {
        case None        =>
          InvalidSmithyShape(ModelIds.fromShapeId(shapeId), "expected a union shape").invalidNel
        case Some(union) =>
          union.getAllMembers.asScala.toList
            .traverse { case (memberName, member) =>
              SmithyNeutralTypeResolver
                .resolveMemberType(
                  model,
                  member,
                  _ => false,
                  (_, memberShape) => sqlTimestampFormat(model, union.getId.getName, memberName, memberShape),
                  SmithyNeutralTypeResolver.MemberContext(
                    shapeId = union.getId,
                    memberName = memberName,
                    role = "union"
                  )
                )
                .map(Variant(memberName, _))
            }
            .map { members =>
              CodegenModel.Union(
                id = ModelIds.fromShapeId(shapeId),
                meta = modelMeta(model, shapeId, SqlMeta.SqlNestedField),
                members = members
              )
            }
      }

    def extractStringEnum(
        model: SmithyModel,
        shapeId: ShapeId
    ): CodegenValidated[CodegenModel.EnumModel[SqlMeta]] =
      model.getShape(shapeId).toScala.flatMap(_.asEnumShape.toScala) match {
        case None            =>
          InvalidSmithyShape(ModelIds.fromShapeId(shapeId), "expected a string enum shape").invalidNel
        case Some(enumShape) =>
          val values =
            enumShape.getEnumValues.asScala.toList.sortBy(_._1).map { case (name, value) =>
              EnumValue(name = name, value = PrimitiveLiteral.StringValue(value))
            }
          CodegenValidated.valid(
            CodegenModel.EnumModel(
              id = ModelIds.fromShapeId(shapeId),
              meta = modelMeta(model, shapeId, SqlMeta.SqlNestedField),
              base = StringT,
              values = values
            )
          )
      }

    def extractIntEnum(model: SmithyModel, shapeId: ShapeId): CodegenValidated[CodegenModel.EnumModel[SqlMeta]] =
      model.getShape(shapeId).toScala.flatMap(_.asIntEnumShape.toScala) match {
        case None            =>
          InvalidSmithyShape(ModelIds.fromShapeId(shapeId), "expected an int enum shape").invalidNel
        case Some(enumShape) =>
          val values =
            enumShape.getEnumValues.asScala.toList.sortBy(_._1).map { case (name, value) =>
              EnumValue(name = name, value = PrimitiveLiteral.IntValue(value.longValue))
            }
          CodegenValidated.valid(
            CodegenModel.EnumModel(
              id = ModelIds.fromShapeId(shapeId),
              meta = modelMeta(model, shapeId, SqlMeta.SqlNestedField),
              base = IntegerT,
              values = values
            )
          )
      }

    def extractAlias(model: SmithyModel, shapeId: ShapeId): CodegenValidated[CodegenModel.Alias[SqlMeta]] =
      SmithyNeutralTypeResolver.aliasUnderlying(model, shapeId).map { underlying =>
        CodegenModel.Alias(
          id = ModelIds.fromShapeId(shapeId),
          meta = modelMeta(model, shapeId, SqlMeta.SqlNestedField),
          underlying = underlying
        )
      }

    def tableMetaForShape(schema: SqlSchema, shapeId: ShapeId): SqlMeta =
      schema.tables.find(_.shapeId == shapeId) match {
        case Some(table) => SqlMeta.SqlTableMeta(tableName = table.name)
        case None        => SqlMeta.SqlNestedField
      }

    def modelMeta(model: SmithyModel, shapeId: ShapeId, feature: SqlMeta): ModelMeta[SqlMeta] = {
      val shape = model.expectShape(shapeId)
      ModelMeta(
        documentation = Option(shape.getTrait(classOf[DocumentationTrait]).orElse(null)).map(_.getValue),
        tags = Option(shape.getTrait(classOf[TagsTrait]).orElse(null)).map(_.getValues.asScala.toList).getOrElse(Nil),
        feature = feature
      )
    }

    def sqlTimestampFormat(
        model: SmithyModel,
        shapeName: String,
        memberName: String,
        member: MemberShape
    ): CodegenValidated[CoreTimestampFormat] =
      SmithyTimestampFormatResolver.resolve(model, member) match {
        case Left(error)   =>
          InvalidSmithyShape(
            ModelIds.fromShapeId(member.getContainer),
            s"$shapeName.$memberName: ${error.detail.getOrElse("unsupported @timestampFormat")}"
          ).invalidNel
        case Right(format) =>
          CodegenValidated.valid(format match {
            case SqlTimestampFormat.Default | SqlTimestampFormat.DateTime => CoreTimestampFormat.DateTime
            case SqlTimestampFormat.EpochSeconds                          => CoreTimestampFormat.EpochSeconds
          })
      }
  }
}
