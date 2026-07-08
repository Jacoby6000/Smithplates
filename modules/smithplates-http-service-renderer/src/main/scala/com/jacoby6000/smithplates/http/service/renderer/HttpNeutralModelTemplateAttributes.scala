package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.codegen.core.EnumValue
import com.jacoby6000.smithplates.codegen.core.Field
import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.NeutralType.*
import com.jacoby6000.smithplates.codegen.core.PrimitiveLiteral
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import com.jacoby6000.smithplates.codegen.core.TypeResolver
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.http.codegen.HttpErrorMeta
import com.jacoby6000.smithplates.http.codegen.HttpMeta

object HttpNeutralModelTemplateAttributes {
  type StructureView = TemplateView[Model.Structure[HttpMeta], HttpMeta]
  type UnionView     = TemplateView[Model.Union[HttpMeta], HttpMeta]
  type EnumView      = TemplateView[Model.EnumModel[HttpMeta], HttpMeta]
  type ModelView[S]  = TemplateView[S, HttpMeta]

  def structureFields(ctx: StructureView): List[Field] =
    ctx.subject.fields

  def problemError(ctx: StructureView): Option[HttpErrorMeta] =
    ctx.subject.meta.feature match {
      case HttpMeta.HttpResponseMeta(_, _, _, error) => error
      case _                                         => None
    }

  def extendsProblem(ctx: StructureView): Boolean =
    problemError(ctx).isDefined

  def httpProblemImportModule[S](ctx: ModelView[S]): String =
    HttpCodegenProblemBase.importModule(ctx)

  def httpProblemClassName: String =
    HttpCodegenProblemBase.ClassName

  def problemDefaultFields(ctx: StructureView): List[(String, String)] = {
    val fieldNames = structureFields(ctx).map(_.name).toSet
    problemError(ctx).toList.flatMap { error =>
      List(
        defaultField("type", error.problemType, fieldNames),
        defaultField("title", error.title, fieldNames),
        defaultField("detail", error.defaultDetail, fieldNames)
      ).flatten
    }
  }

  def importModels[S](ctx: ModelView[S], subject: Model[HttpMeta]): List[Model[HttpMeta]] =
    ctx.usedTypes
      .filterNot(_.id == subject.id)
      .filterNot(_.asAlias.isDefined)
      .distinctBy(_.id)
      .sortBy(model => ctx.conventions.modulePath(model.id))

  def structureNeedsDatetimeImport(ctx: StructureView): Boolean =
    structureFields(ctx).exists(field => typeContainsDatetime(field.tpe, ctx))

  def unionNeedsDatetimeImport(ctx: UnionView): Boolean =
    ctx.subject.members.exists(member => typeContainsDatetime(member.tpe, ctx))

  def fieldName(ctx: StructureView, field: Field): String =
    field.name

  def fieldType(ctx: StructureView, field: Field): String =
    renderType(field.tpe, ctx)

  def fieldDefault(field: Field): String =
    if (isOptional(field.tpe)) {
      "default=None"
    } else {
      "..."
    }

  def className[S](ctx: ModelView[S]): String =
    subjectModel(ctx).map(model => ctx.conventions.className(model.id)).getOrElse("")

  def enumBaseClass(ctx: EnumView): String =
    ctx.subject.base match {
      case IntegerT => "IntEnum"
      case _        => "StrEnum"
    }

  def enumValueName(ctx: EnumView, value: EnumValue): String =
    value.name

  def enumValueLiteral(value: EnumValue): String =
    value.value match {
      case PrimitiveLiteral.StringValue(inner) => pythonStringLiteral(inner)
      case PrimitiveLiteral.IntValue(inner)    => inner.toString
    }

  def unionVariantTypeName(ctx: UnionView, memberName: String): String =
    s"${className(ctx)}${memberName.capitalize}"

  def unionTypeAlias(ctx: UnionView): String =
    ctx.subject.members.map(member => unionVariantTypeName(ctx, member.name)).mkString(" | ")

  def unionFieldName(ctx: UnionView, memberName: String): String =
    memberName

  def unionMemberType(ctx: UnionView, member: com.jacoby6000.smithplates.codegen.core.Variant): String =
    if (unionMemberTypeNameCollidesWithVariant(ctx, member)) {
      s"${modelRefClassName(member.tpe, ctx)}Shape"
    } else {
      renderType(member.tpe, ctx)
    }

  def unionImportedTypeAlias(ctx: UnionView, model: Model[HttpMeta]): String =
    if (ctx.subject.members.exists(member => unionMemberTypeNameCollidesWithVariant(ctx, member))) {
      s"${ctx.conventions.className(model.id)}Shape"
    } else {
      ctx.conventions.className(model.id)
    }

  def validateFunctionName(ctx: UnionView): String =
    s"validate_${ctx.conventions.memberName(ctx.subject.id.name).stripSuffix("_")}"

  def modulePath[S](ctx: ModelView[S], model: Model[HttpMeta]): String =
    ctx.conventions.modulePath(model.id)

  def renderType[S](tpe: NeutralType, ctx: ModelView[S]): String =
    tpe match {
      case OptionalT(inner)                         => s"${renderType(inner, ctx)} | None"
      case ListT(element)                           => s"list[${renderType(element, ctx)}]"
      case MapT(key, value)                         => s"dict[${renderType(key, ctx)}, ${renderType(value, ctx)}]"
      case BooleanT                                 => "bool"
      case IntegerT | LongT | BigIntegerT           => "int"
      case FloatT | DoubleT                         => "float"
      case BigDecimalT                              => "Decimal"
      case StringT                                  => "str"
      case BytesT                                   => "bytes"
      case DocumentT                                => "object"
      case TimestampT(TimestampFormat.EpochSeconds) => "float"
      case TimestampT(TimestampFormat.DateTime)     => "datetime"
      case ref: ModelRef                            =>
        // DESNOTE(jbarber, 2026-07-02): Follow alias chains via the core's
        // @tailrec TypeResolver.underlying (per #34) rather than hand-rolling
        // single-level resolution. Smithy extraction only ever produces
        // primitive alias underlyings (SmithyPrelude.userDefinedAliasUnderlying),
        // so chains cannot arise today, but this keeps the criterion satisfied.
        resolver(ctx).underlying(ref) match {
          case resolved: ModelRef => ctx.conventions.className(resolved.id)
          case other              => renderType(other, ctx)
        }
    }

  def isOptional(tpe: NeutralType): Boolean =
    tpe match {
      case OptionalT(_) => true
      case _            => false
    }

  private def defaultField(
      name: String,
      value: Option[String],
      existingFieldNames: Set[String]
  ): Option[(String, String)] =
    value
      .filter(_ => !existingFieldNames.contains(name))
      .map(inner => name -> pythonStringLiteral(inner))

  private def typeContainsDatetime[S](tpe: NeutralType, ctx: ModelView[S]): Boolean =
    tpe match {
      case TimestampT(TimestampFormat.DateTime) => true
      case OptionalT(inner)                     => typeContainsDatetime(inner, ctx)
      case ListT(element)                       => typeContainsDatetime(element, ctx)
      case MapT(key, value)                     =>
        typeContainsDatetime(key, ctx) || typeContainsDatetime(value, ctx)
      case ref: ModelRef                        =>
        // DESNOTE(jbarber, 2026-07-07): Mirror `renderType`'s stop condition.
        // `TypeResolver.underlying` returns the ref unchanged for a non-alias
        // model reference (structure/union/enum) or an unresolved id, so
        // recursing on that same `ModelRef` loops forever. A reference to
        // another model is imported by class name and never introduces a
        // `datetime` import in this file, so only aliases that resolve to a
        // concrete (non-`ModelRef`) type can require one.
        resolver(ctx).underlying(ref) match {
          case _: ModelRef => false
          case other       => typeContainsDatetime(other, ctx)
        }
      case _                                    => false
    }

  private def unionMemberTypeNameCollidesWithVariant(
      ctx: UnionView,
      member: com.jacoby6000.smithplates.codegen.core.Variant
  ): Boolean =
    unionVariantTypeName(ctx, member.name) == modelRefClassName(member.tpe, ctx)

  private def modelRefClassName[S](tpe: NeutralType, ctx: ModelView[S]): String =
    tpe match {
      case ref: ModelRef => ctx.conventions.className(ref.id)
      case _             => renderType(tpe, ctx)
    }

  private def resolver[S](ctx: ModelView[S]): TypeResolver[HttpMeta] =
    TypeResolver.fromModelSet(ModelSet(subjectModel(ctx).toList ++ ctx.usedTypes))

  private def subjectModel[S](ctx: ModelView[S]): Option[Model[HttpMeta]] =
    ctx.subject match {
      case model: Model[?] => Some(model.asInstanceOf[Model[HttpMeta]])
      case _               => None
    }

  private def pythonStringLiteral(value: String): String =
    "\"" + value.flatMap {
      case '\\' => "\\\\"
      case '"'  => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case ch   => ch.toString
    } + "\""
}
