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
import com.jacoby6000.smithplates.codegen.core.ModelExtractor
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelMeta
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.ModelSetValidator
import com.jacoby6000.smithplates.codegen.core.ModelValidator
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.OperationMeta
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
import com.jacoby6000.smithplates.http.traits.WebsocketTrait
import com.jacoby6000.smithplates.smithy.neutral.*
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

object HttpCoreModelExtractor extends SmithyModelExtractor[HttpMeta, HttpServiceMeta, HttpOperationMeta] {
  import HttpCoreMetaValidator.given

  given ServiceMetaValidator[HttpServiceMeta]                         = ServiceMetaValidator.noop
  given ModelValidator[HttpMeta]                                      = ModelValidator.default
  given OperationValidator[HttpOperationMeta]                         = OperationValidator.default
  given ServiceModelValidator[HttpServiceMeta, HttpOperationMeta]     = ServiceModelValidator.default
  given ModelSetValidator[HttpMeta]                                   = ModelSetValidator.default
  given SystemValidator[HttpMeta, HttpServiceMeta, HttpOperationMeta] = SystemValidator.default

  def extract(
      model: SmithyModel
  ): CodegenValidated[(ModelSet[HttpMeta], List[ServiceModel[HttpServiceMeta, HttpOperationMeta]])] =
    extractValidated(model)(using SystemValidator.default)

  def extractAndValidate(
      model: SmithyModel
  ): CodegenValidated[(ModelSet[HttpMeta], List[ServiceModel[HttpServiceMeta, HttpOperationMeta]])] =
    extract(model)

  override def extractValidated(model: SmithyModel)(using
      validator: SystemValidator[HttpMeta, HttpServiceMeta, HttpOperationMeta]
  ): CodegenValidated[(ModelSet[HttpMeta], List[ServiceModel[HttpServiceMeta, HttpOperationMeta]])] =
    internal
      .extractFromSmithy(model)
      .andThen { case (modelSet, services) =>
        ModelExtractor.validateServices(modelSet, services)(using validator).map(_ => (modelSet, services))
      }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def extractFromSmithy(
        model: SmithyModel
    ): CodegenValidated[(ModelSet[HttpMeta], List[ServiceModel[HttpServiceMeta, HttpOperationMeta]])] =
      HttpServiceExtractor
        .extract(model)
        .leftMap(_.map(error => InvalidSmithyShape(ModelId("smithy", "http"), error.message)))
        .andThen { case (httpServices, _) =>
          httpServices
            .traverse(httpService => convertService(model, httpService))
            .map { results =>
              val services = results.map(_._1)
              val models   = results.flatMap(_._2).groupBy(_.id).view.mapValues(_.head).values.toList.sortBy(_.id)
              (ModelSet(models), services)
            }
        }

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
                successStatus = operation.successStatusCode,
                documentation = operation.documentation,
                inputMembers = operation.inputMembers.map(internal.toInputMemberMeta),
                bodyBinding = internal.toBodyBindingMeta(operation.bodyBinding),
                responseVariants = operation.responseBinding.allVariants.map(internal.toResponseVariantMeta),
                websocket = internal
                  .websocketMeta(model, operation.shapeId, operation.uri),
                authAlternatives = operation.authAlternatives.map(alternative =>
                  HttpAuthAlternativeMeta(ModelIds.fromShapeId(alternative.schemeId)))
              )
            ),
            input = if (operation.inputShape == HttpStructureExtractor.internal.UnitShapeId) {
              None
            } else {
              Some(ModelRef(ModelIds.fromShapeId(operation.inputShape)))
            },
            output = operation.outputShape
              .filter(_ != HttpStructureExtractor.internal.UnitShapeId)
              .map(shapeId => ModelRef(ModelIds.fromShapeId(shapeId))),
            errors = operation.operationErrors.map(error => ModelRef(ModelIds.fromShapeId(error.shapeId)))
          )
        }

      val httpOperations         = httpService.routeGroups.flatMap(_.operations)
      val serviceErrorShapeIds   = httpService.serviceErrors.map(_.shapeId).toSet
      val operationErrors        = httpOperations.flatMap(_.operationErrors)
      val operationErrorShapeIds = operationErrors.map(_.shapeId).toSet
      val errorStructureShapeIds = serviceErrorShapeIds ++ operationErrorShapeIds
      val operationShapeIds      =
        httpOperations
          .flatMap { operation =>
            List(operation.inputShape) ++ operation.outputShape.toList
          }
          .filter(_ != HttpStructureExtractor.internal.UnitShapeId)
          .distinct
      val existingStructureIds   = httpService.structures.map(_.shapeId).toSet
      val existingUnionIds       = httpService.unions.map(_.shapeId).toSet
      val extraStructureIds      =
        operationShapeIds
          .filterNot(existingStructureIds.contains)
          .filterNot(existingUnionIds.contains)
          .filterNot(errorStructureShapeIds.contains)

      val service = ServiceModel(
        id = serviceId,
        meta = ServiceMeta(
          documentation = httpService.documentation,
          tags = Nil,
          feature = HttpServiceMeta(
            title = httpService.title,
            version = httpService.version,
            serviceErrors = httpService.serviceErrors.map { error =>
              HttpServiceErrorMeta(
                id = ModelIds.fromShapeId(error.shapeId),
                statusCode = error.statusCode,
                error = error.problemBinding.map { binding =>
                  HttpErrorMeta(
                    problemType = Some(binding.problemType),
                    title = Some(binding.title),
                    defaultDetail = binding.defaultDetail
                  )
                },
                responseVariant = internal.toResponseVariantMeta(error.responseVariant)
              )
            },
            modelNamespaces = internal.modelNamespaces(httpService, extraStructureIds),
            emittedModelIds = internal.emittedModelIds(httpService, extraStructureIds),
            authSchemes = httpService.authSchemes.map(internal.toAuthSchemeMeta)
          )
        ),
        operations = operations
      )

      val errorVariantsByShapeId =
        httpOperations
          .flatMap(_.responseBinding.errorVariants)
          .groupBy(_.modelShapeId)
          .view
          .mapValues(_.head)
          .toMap

      val rootShapeIds =
        httpService.structures.map(_.shapeId) ++
          httpService.unions.map(_.shapeId) ++
          httpService.stringEnums.map(_.shapeId) ++
          httpService.intEnums.map(_.shapeId) ++
          httpService.serviceErrors.map(_.shapeId) ++
          extraStructureIds

      val aliasIds = SmithyAliasClosure.aliasShapeIds(model, rootShapeIds.distinct)

      HttpCoreMetaBuilder.buildOperationShapeMeta(model, httpService.shapeId, httpOperations).andThen {
        operationShapeMeta =>
          (
            aliasIds.traverse(shapeId => extractAlias(model, shapeId)),
            httpService.structures
              .filterNot(structure => errorStructureShapeIds.contains(structure.shapeId))
              .traverse(structure =>
                extractStructure(model, httpService.shapeId, structure.shapeId, operationShapeMeta)),
            extraStructureIds.traverse(shapeId =>
              extractStructure(model, httpService.shapeId, shapeId, operationShapeMeta)),
            httpService.unions.traverse(union => extractUnion(model, httpService.shapeId, union.shapeId)),
            httpService.stringEnums.traverse(stringEnum => extractStringEnum(model, stringEnum.shapeId)),
            httpService.intEnums.traverse(intEnum => extractIntEnum(model, intEnum.shapeId)),
            httpService.serviceErrors.traverse(error => extractErrorStructure(model, httpService.shapeId, error)),
            operationErrors.traverse(error =>
              extractOperationErrorStructure(
                model,
                httpService.shapeId,
                error,
                errorVariantsByShapeId.get(error.shapeId)))
          ).mapN {
            case (
                  aliases,
                  structures,
                  extraStructures,
                  unions,
                  stringEnums,
                  intEnums,
                  serviceErrorStructures,
                  operationErrorStructures) =>
              (
                service,
                aliases ++ structures ++ extraStructures ++ unions ++ stringEnums ++ intEnums ++ serviceErrorStructures ++
                  operationErrorStructures
              )
          }
      }
    }

    def extractOperationErrorStructure(
        model: SmithyModel,
        serviceShape: ShapeId,
        error: HttpOperationError,
        responseVariant: Option[HttpResponseVariant]
    ): CodegenValidated[CodegenModel.Structure[HttpMeta]] =
      extractStructure(model, serviceShape, error.shapeId, HttpCoreMetaBuilder.emptyOperationShapeMeta).map {
        structure =>
          structure.copy(
            meta = structure.meta.copy(
              feature = HttpMeta.HttpResponseMeta(
                statusCode = error.statusCode,
                staticHeaders = responseVariant.map(_.staticHeaders.toMap).getOrElse(Map.empty),
                dynamicHeaderFields = responseVariant.map(_.headerBindings.toMap).getOrElse(Map.empty),
                error = HttpProblemBindingExtractor.extract(model, error.shapeId).map { binding =>
                  HttpErrorMeta(
                    problemType = Some(binding.problemType),
                    title = Some(binding.title),
                    defaultDetail = binding.defaultDetail
                  )
                }
              )
            )
          )
      }

    def extractStructure(
        model: SmithyModel,
        serviceShape: ShapeId,
        shapeId: ShapeId,
        operationShapeMeta: HttpCoreMetaBuilder.OperationShapeMeta
    ): CodegenValidated[CodegenModel.Structure[HttpMeta]] =
      model.getShape(shapeId).toScala.flatMap(_.asStructureShape.toScala) match {
        case None            =>
          InvalidSmithyShape(ModelIds.fromShapeId(shapeId), "expected a structure shape").invalidNel
        case Some(structure) =>
          val feature = HttpCoreMetaBuilder.structureFeature(shapeId, operationShapeMeta)
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
                meta = modelMeta(model, shapeId, feature),
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
      extractStructure(model, serviceShape, error.shapeId, HttpCoreMetaBuilder.emptyOperationShapeMeta).map {
        structure =>
          structure.copy(
            meta = structure.meta.copy(
              feature = HttpMeta.HttpResponseMeta(
                statusCode = error.statusCode,
                staticHeaders = error.responseVariant.staticHeaders.toMap,
                dynamicHeaderFields = error.responseVariant.headerBindings.toMap,
                error = error.problemBinding.map { binding =>
                  HttpErrorMeta(
                    problemType = Some(binding.problemType),
                    title = Some(binding.title),
                    defaultDetail = binding.defaultDetail
                  )
                }
              )
            )
          )
      }

    def toResponseVariantMeta(variant: HttpResponseVariant): HttpResponseVariantMeta =
      HttpResponseVariantMeta(
        variantTypeName = variant.variantTypeName,
        statusCode = variant.statusCode,
        mediaType = variant.mediaType,
        headerBindings = variant.headerBindings,
        staticHeaders = variant.staticHeaders,
        modelShapeId = if (variant.modelShapeId == HttpStructureExtractor.internal.UnitShapeId) {
          None
        } else {
          Some(ModelIds.fromShapeId(variant.modelShapeId))
        }
      )

    def toAuthSchemeMeta(scheme: HttpAuthScheme): HttpAuthSchemeMeta =
      scheme match {
        case HttpAuthScheme.Bearer(id)                       =>
          HttpAuthSchemeMeta.Bearer(ModelIds.fromShapeId(id))
        case HttpAuthScheme.ApiKey(id, name, location, auth) =>
          HttpAuthSchemeMeta.ApiKey(
            id = ModelIds.fromShapeId(id),
            name = name,
            location = location match {
              case HttpApiKeyLocation.Header => HttpApiKeyLocationMeta.Header
              case HttpApiKeyLocation.Query  => HttpApiKeyLocationMeta.Query
            },
            scheme = auth
          )
        case HttpAuthScheme.Cookie(id, name)                 =>
          HttpAuthSchemeMeta.Cookie(ModelIds.fromShapeId(id), name)
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

    def websocketMeta(model: SmithyModel, operationShapeId: ShapeId, path: String): Option[HttpWebsocketMeta] =
      model.getShape(operationShapeId).toScala.flatMap(_.getTrait(classOf[WebsocketTrait]).toScala).map { _ =>
        HttpWebsocketMeta(path = path)
      }

    def toInputMemberMeta(member: HttpOperationInputMember): HttpOperationInputMemberMeta =
      HttpOperationInputMemberMeta(
        name = member.name,
        typeName = member.typeName,
        timestampFormat = member.timestampFormat,
        required = member.required,
        binding = toInputBindingMeta(member.binding)
      )

    def toBodyBindingMeta(binding: HttpOperationBodyBinding): HttpOperationBodyBindingMeta =
      binding match {
        case HttpOperationBodyBinding.None                   => HttpOperationBodyBindingMeta.None
        case document: HttpOperationBodyBinding.Document     =>
          HttpOperationBodyBindingMeta.Document(document.inputShape.getName)
        case members: HttpOperationBodyBinding.Members       =>
          HttpOperationBodyBindingMeta.Members(members.members.map(toInputMemberMeta))
        case nested: HttpOperationBodyBinding.NestedDocument =>
          HttpOperationBodyBindingMeta.NestedDocument(
            inputShapeName = nested.inputShape.getName,
            payloadMemberName = nested.payloadMember.name,
            payloadTargetShapeName = nested.payloadMember.typeName
          )
      }

    def toInputBindingMeta(binding: HttpInputMemberBinding): HttpInputMemberBindingMeta =
      binding match {
        case HttpInputMemberBinding.PathLabel()        => HttpInputMemberBindingMeta.PathLabel
        case HttpInputMemberBinding.Query(queryName)   => HttpInputMemberBindingMeta.Query(queryName)
        case HttpInputMemberBinding.Header(headerName) => HttpInputMemberBindingMeta.Header(headerName)
        case HttpInputMemberBinding.Payload()          => HttpInputMemberBindingMeta.Payload
      }

    def modelNamespaces(httpService: HttpService, extraShapeIds: List[ShapeId] = Nil): Map[String, String] =
      (
        httpService.structures.map(shape => shape.name -> shape.shapeId.getNamespace) ++
          httpService.unions.map(shape => shape.name -> shape.shapeId.getNamespace) ++
          httpService.stringEnums.map(shape => shape.name -> shape.shapeId.getNamespace) ++
          httpService.intEnums.map(shape => shape.name -> shape.shapeId.getNamespace) ++
          httpService.serviceErrors.map(error => error.name -> error.shapeId.getNamespace) ++
          extraShapeIds.map(shapeId => shapeId.getName -> shapeId.getNamespace)
      ).toMap

    def emittedModelIds(httpService: HttpService, extraShapeIds: List[ShapeId] = Nil): Set[ModelId] =
      (
        httpService.structures.map(shape => ModelIds.fromShapeId(shape.shapeId)) ++
          httpService.unions.map(shape => ModelIds.fromShapeId(shape.shapeId)) ++
          httpService.stringEnums.map(shape => ModelIds.fromShapeId(shape.shapeId)) ++
          httpService.intEnums.map(shape => ModelIds.fromShapeId(shape.shapeId)) ++
          extraShapeIds.map(shapeId => ModelIds.fromShapeId(shapeId))
      ).toSet

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
