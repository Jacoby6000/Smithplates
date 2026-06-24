package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef

import scala.annotation.tailrec

trait TypeResolver[A] {
  def resolve(ref: ModelRef): Option[Model[A]]

  /** Follow `Alias` chains (including alias-to-alias) to the first non-alias type; returns `tpe` unchanged otherwise.
    */
  // DESNOTE(jbarber, 2026-06-23): Cyclic alias definitions are rejected by
  // [[ModelSetValidator]] / [[SystemValidator]] before codegen reaches
  // `TypeResolver`; callers should validate first. Cycle handling is not part
  // of the public contract and is not covered in `TypeResolverSpec`.
  def underlying(tpe: NeutralType): NeutralType

  def classify(ref: ModelRef): Option[ModelKind]
}

object TypeResolver {
  def fromModelSet[A](modelSet: ModelSet[A]): TypeResolver[A] =
    new TypeResolver[A] {
      def resolve(ref: ModelRef): Option[Model[A]] = modelSet.resolve(ref)

      def classify(ref: ModelRef): Option[ModelKind] = modelSet.resolve(ref).map(_.kind)

      @tailrec
      def underlying(tpe: NeutralType): NeutralType =
        tpe match {
          case ref: ModelRef =>
            modelSet.resolve(ref).flatMap(_.asAlias) match {
              case Some(alias) => underlying(alias.underlying)
              case None        => tpe
            }
          case _             => tpe
        }
    }
}
