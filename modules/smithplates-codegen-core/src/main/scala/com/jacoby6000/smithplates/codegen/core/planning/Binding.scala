package com.jacoby6000.smithplates.codegen.core.planning

import cats.Eq
import cats.derived.semiauto
import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelKind
import com.jacoby6000.smithplates.codegen.core.OperationModel

sealed trait BindingGroup

object BindingGroup {
  case object All  extends BindingGroup
  case object None extends BindingGroup
  case object Tag  extends BindingGroup

  given Eq[BindingGroup] = semiauto.eq
}

sealed trait BindingFilterAtom

object BindingFilterAtom {
  case object All                        extends BindingFilterAtom
  case object Tagged                     extends BindingFilterAtom
  case object Untagged                   extends BindingFilterAtom
  final case class Kind(kind: ModelKind) extends BindingFilterAtom

  given Eq[BindingFilterAtom] = semiauto.eq
}

sealed trait SmithyBinding

object SmithyBinding {
  case object Service                                                                 extends SmithyBinding
  case object Once                                                                    extends SmithyBinding
  final case class Operation(filters: List[BindingFilterAtom], groupBy: BindingGroup) extends SmithyBinding
  final case class Model(filters: List[BindingFilterAtom], groupBy: BindingGroup)     extends SmithyBinding

  given Eq[SmithyBinding] = semiauto.eq
}

object BindingFilter {
  def matchesModel[A](filters: List[BindingFilterAtom], model: Model[A]): Boolean =
    effectiveFilters(filters).forall {
      case BindingFilterAtom.All        => true
      case BindingFilterAtom.Tagged     => model.meta.tags.nonEmpty
      case BindingFilterAtom.Untagged   => model.meta.tags.isEmpty
      case BindingFilterAtom.Kind(kind) => model.kind == kind
    }

  def matchesOperation[A](filters: List[BindingFilterAtom], operation: OperationModel[A]): Boolean =
    effectiveFilters(filters).forall {
      case BindingFilterAtom.All      => true
      case BindingFilterAtom.Tagged   => operation.meta.tags.nonEmpty
      case BindingFilterAtom.Untagged => operation.meta.tags.isEmpty
      case BindingFilterAtom.Kind(_)  => false
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def effectiveFilters(filters: List[BindingFilterAtom]): List[BindingFilterAtom] =
      if (filters.isEmpty) {
        List(BindingFilterAtom.All)
      } else {
        filters
      }
  }

  private def effectiveFilters(filters: List[BindingFilterAtom]): List[BindingFilterAtom] =
    internal.effectiveFilters(filters)
}
