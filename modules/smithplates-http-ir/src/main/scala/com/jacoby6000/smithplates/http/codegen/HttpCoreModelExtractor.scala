package com.jacoby6000.smithplates.http.codegen

import cats.kernel.instances.order.catsKernelOrderingForOrder
import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.invalidNel
import com.jacoby6000.smithplates.codegen.core.CodegenValidationError
import com.jacoby6000.smithplates.codegen.core.EnumValue
import com.jacoby6000.smithplates.codegen.core.Field
import com.jacoby6000.smithplates.codegen.core.InvalidSmithyShape
import com.jacoby6000.smithplates.codegen.core.Model as CodegenModel
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
import com.jacoby6000.smithplates.http.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.smithy.neutral.*
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object HttpCoreModelExtractor extends SmithyModelExtractor[HttpMeta, HttpServiceMeta, HttpOperationMeta] {
  given ModelMetaValidator[HttpMeta]                                  = ModelMetaValidator.noop
  given OperationMetaValidator[HttpOperationMeta]                     = OperationMetaValidator.noop
  given ServiceMetaValidator[HttpServiceMeta]                         = ServiceMetaValidator.noop
  given ModelValidator[HttpMeta]                                      = ModelValidator.default
  given OperationValidator[HttpOperationMeta]                         = OperationValidator.default
  given ServiceModelValidator[HttpServiceMeta, HttpOperationMeta]     = ServiceModelValidator.default
  given ModelSetValidator[HttpMeta]                                   = ModelSetValidator.default
  given SystemValidator[HttpMeta, HttpServiceMeta, HttpOperationMeta] = SystemValidator.default

  def extract(
      model: SmithyModel
  ): CodegenValidated[(ModelSet[HttpMeta], List[ServiceModel[HttpServiceMeta, HttpOperationMeta]])] =
    HttpServiceExtractor
      .extract(model)
      .leftMap(_.map(error => InvalidSmithyShape(ModelId("smithy", "http"), error.message)))
      .andThen { case (httpServices, _) =>
        httpServices
          .traverse(httpService => internal.convertService(model, httpService))
          .map { results =>
            val services = results.map(_._1)
            val models   = results.flatMap(_._2).distinct.sortBy(_.id)
            (ModelSet(models), services)
          }
      }

  def extractAndValidate(
      model: SmithyModel
  ): CodegenValidated[(ModelSet[HttpMeta], List[ServiceModel[HttpServiceMeta, HttpOperationMeta]])] =
    extractValidated(model)(using SystemValidator.default)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def convertService(
        model: SmithyModel,
        httpService: HttpService
    ): CodegenValidated[(ServiceModel[HttpServiceMeta, HttpOperationMeta], List[CodegenModel[HttpMeta]])] = {
      val serviceId  = ModelIds.fromShapeId(httpService.shapeId)
      val operations =
        httpService.routeGroups.flatMap(_.operations).map { operation =>
          OperationModel(
            id = ModelIds.fromShapeId(operation.shapeId),
            meta = OperationMeta(
              documentation = operation.documentation,
              tags = operation.tags,
              feature = HttpOperationMeta(
                method = operation.method,
                uriPattern = operation.uri,
                successStatus = operation.successStatusCode
              )
            ),
            input = if (operation.inputShape == HttpStructureExtractor.internal.UnitShapeId) {
              None
            } else {
              Some(ModelRef(ModelIds.fromShapeId(operation.inputShape)))
            },
            output = operation.outputShape.map(shapeId => ModelRef(ModelIds.fromShapeId(shapeId))),
            errors = operation.operationErrors.map(error => ModelRef(ModelIds.fromShapeId(error.shapeId)))
          )
        }

      val service = ServiceModel(
        id = serviceId,
        meta = ServiceMeta(
          documentation = httpService.documentation,
          tags = Nil,
          feature = HttpServiceMeta()
        ),
        operations = operations
      )

      val rootShapeIds =
        httpService.structures.map(_.shapeId) ++
          httpService.unions.map(_.shapeId) ++
          httpService.stringEnums.map(_.shapeId) ++
          httpService.intEnums.map(_.shapeId) ++
          httpService.serviceErrors.map(_.shapeId)

      val aliasIds = collectAliasShapeIds(model, rootShapeIds)

      val operationShapeIds =
        httpService.routeGroups
          .flatMap(_.operations)
          .flatMap { operation =>
            List(operation.inputShape) ++ operation.outputShape.toList
          }
          .filter(_ != HttpStructureExtractor.internal.UnitShapeId)
          .distinct

      val existingStructureIds = httpService.structures.map(_.shapeId).toSet
      val extraStructureIds    = operationShapeIds.filterNot(existingStructureIds.contains)

      (
        aliasIds.traverse(shapeId => extractAlias(model, shapeId)),
        httpService.structures.traverse(structure => extractStructure(model, httpService.shapeId, structure.shapeId)),
        extraStructureIds.traverse(shapeId => extractStructure(model, httpService.shapeId, shapeId)),
        httpService.unions.traverse(union => extractUnion(model, httpService.shapeId, union.shapeId)),
        httpService.stringEnums.traverse(stringEnum => extractStringEnum(model, stringEnum.shapeId)),
        httpService.intEnums.traverse(intEnum => extractIntEnum(model, intEnum.shapeId)),
        httpService.serviceErrors.traverse(error => extractErrorStructure(model, httpService.shapeId, error))
      ).mapN { case (aliases, structures, extraStructures, unions, stringEnums, intEnums, errorStructures) =>
        (
          service,
          aliases ++ structures ++ extraStructures ++ unions ++ stringEnums ++ intEnums ++ errorStructures
        )
      }
    }

    def collectAliasShapeIds(model: SmithyModel, rootShapeIds: List[ShapeId]): List[ShapeId] =
      rootShapeIds
        .flatMap(referencedMemberTargets(model, _))
        .filter { shapeId =>
          val shape = model.expectShape(shapeId)
          shape.isStringShape && !SmithyPrelude.isPreludeShape(shapeId)
        }
        .distinct

    def referencedMemberTargets(model: SmithyModel, shapeId: ShapeId): List[ShapeId] =
      model.getShape(shapeId).toScala.toList.flatMap {
        case shape if shape.isStructureShape =>
          shape.asStructureShape.get().getAllMembers.asScala.values.toList.map(_.getTarget)
        case shape if shape.isUnionShape     =>
          shape.asUnionShape.get().getAllMembers.asScala.values.toList.map(_.getTarget)
        case _                               =>
          Nil
      }

    def extractStructure(
        model: SmithyModel,
        serviceShape: ShapeId,
        shapeId: ShapeId
    ): CodegenValidated[CodegenModel.Structure[HttpMeta]] =
      model.getShape(shapeId).toScala.flatMap(_.asStructureShape.toScala) match {
        case None            =>
          InvalidSmithyShape(ModelIds.fromShapeId(shapeId), "expected a structure shape").invalidNel
        case Some(structure) =>
          structure.getAllMembers.asScala.toList
            .traverse { case (memberName, member) =>
              SmithyNeutralTypeResolver
                .resolveMemberType(
                  model,
                  member,
                  SmithyNeutralTypeResolver.memberOptionalByRequiredTrait,
                  (_, memberShape) =>
                    httpTimestampFormat(model, serviceShape, structure.getId.getName, memberName, memberShape),
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
                meta = modelMeta(model, shapeId, HttpMeta.HttpNestedField),
                fields = fields
              )
            }
      }

    def extractUnion(
        model: SmithyModel,
        serviceShape: ShapeId,
        shapeId: ShapeId
    ): CodegenValidated[CodegenModel.Union[HttpMeta]] =
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
                  (_, memberShape) =>
                    httpTimestampFormat(model, serviceShape, union.getId.getName, memberName, memberShape),
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
                meta = modelMeta(model, shapeId, HttpMeta.HttpNestedField),
                members = members
              )
            }
      }

    def extractStringEnum(model: SmithyModel, shapeId: ShapeId): CodegenValidated[CodegenModel.EnumModel[HttpMeta]] =
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
              meta = modelMeta(model, shapeId, HttpMeta.HttpNestedField),
              base = StringT,
              values = values
            )
          )
      }

    def extractIntEnum(model: SmithyModel, shapeId: ShapeId): CodegenValidated[CodegenModel.EnumModel[HttpMeta]] =
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
              meta = modelMeta(model, shapeId, HttpMeta.HttpNestedField),
              base = IntegerT,
              values = values
            )
          )
      }

    def extractAlias(model: SmithyModel, shapeId: ShapeId): CodegenValidated[CodegenModel.Alias[HttpMeta]] =
      SmithyNeutralTypeResolver.aliasUnderlying(model, shapeId).map { underlying =>
        CodegenModel.Alias(
          id = ModelIds.fromShapeId(shapeId),
          meta = modelMeta(model, shapeId, HttpMeta.HttpNestedField),
          underlying = underlying
        )
      }

    def extractErrorStructure(
        model: SmithyModel,
        serviceShape: ShapeId,
        error: HttpServiceError
    ): CodegenValidated[CodegenModel.Structure[HttpMeta]] =
      extractStructure(model, serviceShape, error.shapeId).map { structure =>
        structure.copy(
          meta = structure.meta.copy(
            feature = HttpMeta.HttpResponseMeta(
              statusCode = error.statusCode,
              error = error.problemBinding.map { binding =>
                HttpErrorMeta(
                  problemType = Some(binding.problemType),
                  title = Some(binding.title)
                )
              }
            )
          )
        )
      }

    def modelMeta(model: SmithyModel, shapeId: ShapeId, feature: HttpMeta): ModelMeta[HttpMeta] = {
      val shape = model.expectShape(shapeId)
      ModelMeta(
        documentation = shape.documentationText,
        tags = shape.tags,
        feature = feature
      )
    }

    def httpTimestampFormat(
        model: SmithyModel,
        serviceShape: ShapeId,
        shapeName: String,
        memberName: String,
        member: MemberShape
    ): CodegenValidated[CoreTimestampFormat] =
      SmithyHttpTimestampFormatResolver
        .resolve(model, serviceShape, shapeName, memberName, member)
        .leftMap(_.map(httpErrorToCodegenError(serviceShape, shapeName, memberName)))
        .map {
          case HttpTimestampFormat.DateTime     => CoreTimestampFormat.DateTime
          case HttpTimestampFormat.EpochSeconds => CoreTimestampFormat.EpochSeconds
          case HttpTimestampFormat.HttpDate     => CoreTimestampFormat.DateTime
        }

    def httpErrorToCodegenError(
        serviceShape: ShapeId,
        shapeName: String,
        memberName: String
    )(error: HttpSchemaError): CodegenValidationError =
      InvalidSmithyShape(
        ModelIds.fromShapeId(serviceShape),
        s"$shapeName.$memberName: ${error.message}"
      )
  }
}
