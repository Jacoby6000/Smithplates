package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.ModelSet
import com.jacoby6000.smithplates.codegen.core.OperationModel
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.TypeUsageAnalyzer
import munit.Assertions

/** Asserts every [[ModelRef]] in extracted models and operation IO resolves within a [[ModelSet]]. */
object ModelSetClosureAssertions extends Assertions {
  def assertAllModelRefsResolved[A, S, O](
      modelSet: ModelSet[A],
      services: List[ServiceModel[S, O]]
  ): Unit = {
    val modelRefs =
      modelSet.all.flatMap(TypeUsageAnalyzer.default.usedTypes) ++
        services.flatMap(_.operations).flatMap(TypeUsageAnalyzer.default.usedTypes)

    modelRefs.distinct.foreach { ref =>
      assert(
        modelSet.resolve(ref).isDefined,
        s"unresolved model reference ${ref.id.namespace}#${ref.id.name}"
      )
    }
  }

  def assertOperationRefsResolved[A, S, O](
      modelSet: ModelSet[A],
      operations: List[OperationModel[O]]
  ): Unit =
    operations.flatMap(TypeUsageAnalyzer.default.usedTypes).distinct.foreach { ref =>
      assert(
        modelSet.resolve(ref).isDefined,
        s"unresolved operation model reference ${ref.id.namespace}#${ref.id.name}"
      )
    }
}
