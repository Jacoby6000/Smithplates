package com.jacoby6000.smithplates.codegen.core

import cats.Eq
import cats.derived.semiauto

final case class ModelMeta[A](
    documentation: Option[String],
    tags: List[String],
    feature: A,
    traits: List[AppliedTrait] = Nil
)

object ModelMeta {
  given [A: Eq]: Eq[ModelMeta[A]] = semiauto.eq
}

final case class ServiceMeta[A](
    documentation: Option[String],
    tags: List[String],
    feature: A,
    traits: List[AppliedTrait] = Nil
)

object ServiceMeta {
  given [A: Eq]: Eq[ServiceMeta[A]] = semiauto.eq
}

final case class OperationMeta[A](
    documentation: Option[String],
    tags: List[String],
    feature: A,
    traits: List[AppliedTrait] = Nil
)

object OperationMeta {
  given [A: Eq]: Eq[OperationMeta[A]] = semiauto.eq
}
