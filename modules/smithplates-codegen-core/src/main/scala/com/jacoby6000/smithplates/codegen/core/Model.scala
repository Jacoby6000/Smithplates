package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef

final case class Field(name: String, tpe: NeutralType)

final case class Variant(name: String, tpe: NeutralType)

sealed trait PrimitiveLiteral

object PrimitiveLiteral {
  final case class StringValue(value: String) extends PrimitiveLiteral
  final case class IntValue(value: Long)      extends PrimitiveLiteral
}

final case class EnumValue(name: String, value: PrimitiveLiteral)

sealed trait Model[A] {
  def id: ModelId
  def meta: ModelMeta[A]
  def kind: ModelKind

  def asStructure: Option[Model.Structure[A]] = None
  def asUnion: Option[Model.Union[A]]         = None
  def asEnum: Option[Model.EnumModel[A]]      = None
  def asAlias: Option[Model.Alias[A]]         = None
}

object Model {
  final case class Structure[A](
      id: ModelId,
      meta: ModelMeta[A],
      fields: List[Field]
  ) extends Model[A] {
    override def kind: ModelKind                   = ModelKind.Structure
    override def asStructure: Option[Structure[A]] = Some(this)
  }

  final case class Union[A](
      id: ModelId,
      meta: ModelMeta[A],
      members: List[Variant]
  ) extends Model[A] {
    override def kind: ModelKind           = ModelKind.Union
    override def asUnion: Option[Union[A]] = Some(this)
  }

  final case class EnumModel[A](
      id: ModelId,
      meta: ModelMeta[A],
      base: NeutralType,
      values: List[EnumValue]
  ) extends Model[A] {
    override def kind: ModelKind              = ModelKind.Enum
    override def asEnum: Option[EnumModel[A]] = Some(this)
  }

  final case class Alias[A](
      id: ModelId,
      meta: ModelMeta[A],
      underlying: NeutralType
  ) extends Model[A] {
    override def kind: ModelKind           = ModelKind.Alias
    override def asAlias: Option[Alias[A]] = Some(this)
  }
}

final case class ModelSet[A](all: List[Model[A]]) {

  /** Internal — not part of the stable API; subject to change without notice. */
  lazy val byId: Map[ModelId, Model[A]] = all.iterator.map(m => m.id -> m).toMap

  def structures: List[Model.Structure[A]] = all.flatMap(_.asStructure)
  def unions: List[Model.Union[A]]         = all.flatMap(_.asUnion)
  def enums: List[Model.EnumModel[A]]      = all.flatMap(_.asEnum)
  def aliases: List[Model.Alias[A]]        = all.flatMap(_.asAlias)

  def resolve(id: ModelId): Option[Model[A]]   = byId.get(id)
  def resolve(ref: ModelRef): Option[Model[A]] = byId.get(ref.id)
}

final case class ServiceModel[A, B](
    id: ModelId,
    meta: ServiceMeta[A],
    operations: List[OperationModel[B]]
)

final case class OperationModel[A](
    id: ModelId,
    meta: OperationMeta[A],
    input: Option[ModelRef],
    output: Option[ModelRef],
    errors: List[ModelRef]
)
