package com.jacoby6000.smithplates.codegen.core

import com.jacoby6000.smithplates.codegen.core.NeutralType.ListT
import com.jacoby6000.smithplates.codegen.core.NeutralType.MapT
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef
import com.jacoby6000.smithplates.codegen.core.NeutralType.OptionalT

/** Feature-agnostic analysis of the model references a model or operation uses directly. Methods are independently
  * generic so the trait carries no feature type parameter.
  */
trait TypeUsageAnalyzer {

  /** Direct `ModelRef`s used by `model`, in declaration order, deduped to first occurrence. */
  def usedTypes[A](model: Model[A]): List[ModelRef]

  /** Direct `ModelRef`s used by `op` (input, then output, then errors), deduped to first occurrence. */
  def usedTypes[A](op: OperationModel[A]): List[ModelRef]
}

object TypeUsageAnalyzer {

  /** `ModelRef`s reachable from `tpe` without crossing into a referenced model, in first-occurrence order, recursing
    * through `OptionalT`/`ListT`/`MapT` (map key before value).
    */
  def directRefs(tpe: NeutralType): List[ModelRef] =
    tpe match {
      case ref: ModelRef    => List(ref)
      case OptionalT(inner) => directRefs(inner)
      case ListT(element)   => directRefs(element)
      case MapT(key, value) => directRefs(key) ++ directRefs(value)
      case _                => Nil
    }

  val default: TypeUsageAnalyzer =
    new TypeUsageAnalyzer {
      def usedTypes[A](model: Model[A]): List[ModelRef] = {
        val declared: List[NeutralType] =
          model match {
            case s: Model.Structure[?] => s.fields.map(_.tpe)
            case u: Model.Union[?]     => u.members.map(_.tpe)
            case a: Model.Alias[?]     => List(a.underlying)
            case _: Model.EnumModel[?] => Nil
          }
        declared.flatMap(directRefs).distinct
      }

      def usedTypes[A](op: OperationModel[A]): List[ModelRef] =
        (op.input.toList ++ op.output.toList ++ op.errors).flatMap(directRefs).distinct
    }
}
