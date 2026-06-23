package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef

import scala.annotation.tailrec

trait TypeResolver[A] {
  def resolve(ref: ModelRef): Option[Model[A]]

  /** Follow `Alias` chains (including alias-to-alias) to the first non-alias type; returns `tpe` unchanged otherwise.
    */
  def underlying(tpe: NeutralType): NeutralType

  def classify(ref: ModelRef): Option[ModelKind]
}

object TypeResolver {
  def fromModelSet[A](modelSet: ModelSet[A]): TypeResolver[A] =
    new TypeResolver[A] {
      def resolve(ref: ModelRef): Option[Model[A]] = modelSet.resolve(ref)

      def classify(ref: ModelRef): Option[ModelKind] = modelSet.resolve(ref).map(_.kind)

      def underlying(tpe: NeutralType): NeutralType = {
        // `seen` guards against cyclic alias definitions (`A = B`, `B = A`).
        @tailrec
        def chase(current: NeutralType, seen: Set[ModelId]): NeutralType =
          current match {
            case ref @ ModelRef(id) if !seen.contains(id) =>
              modelSet.resolve(ref).flatMap(_.asAlias) match {
                case Some(alias) => chase(alias.underlying, seen + id)
                case None        => current
              }
            case _                                        => current
          }

        chase(tpe, Set.empty)
      }
    }
}
