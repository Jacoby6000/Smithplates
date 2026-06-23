package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import cats.kernel.instances.order.catsKernelOrderingForOrder
import com.jacoby6000.smithplates.codegen.core.NeutralType.ModelRef

/** Shared structural validation helpers used by component validators and [[SystemValidator]]. */
object Validation {

  final case class IdOccurrenceCounts(models: Int = 0, services: Int = 0, operations: Int = 0) {
    def total: Int = models + services + operations

    def +(other: IdOccurrenceCounts): IdOccurrenceCounts =
      IdOccurrenceCounts(
        models = models + other.models,
        services = services + other.services,
        operations = operations + other.operations
      )
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def idOccurrencesFromModels[A](models: List[Model[A]]): Map[ModelId, IdOccurrenceCounts] =
      models
        .groupBy(_.id)
        .view
        .mapValues(grouped => IdOccurrenceCounts(models = grouped.size))
        .toMap

    def idOccurrencesFromService[B, C](service: ServiceModel[B, C]): Map[ModelId, IdOccurrenceCounts] = {
      val operationOccurrences =
        service.operations
          .groupBy(_.id)
          .view
          .mapValues(grouped => IdOccurrenceCounts(operations = grouped.size))
          .toMap
      mergeIdOccurrences(
        operationOccurrences,
        Map(service.id -> IdOccurrenceCounts(services = 1))
      )
    }

    def mergeIdOccurrences(maps: Map[ModelId, IdOccurrenceCounts]*): Map[ModelId, IdOccurrenceCounts] =
      maps.foldLeft(Map.empty[ModelId, IdOccurrenceCounts]) { (acc, next) =>
        next.foldLeft(acc) { case (merged, (id, counts)) =>
          merged.updated(id, merged.getOrElse(id, IdOccurrenceCounts()) + counts)
        }
      }

    def duplicateIds(occurrences: Map[ModelId, IdOccurrenceCounts]): List[DuplicateId] =
      occurrences.toList
        .collect {
          case (id, counts) if counts.total > 1 => DuplicateId(id, counts.models, counts.services, counts.operations)
        }
        .sortBy(_.id)

    def duplicateIdsFromModels[A](models: List[Model[A]]): List[DuplicateId] =
      duplicateIds(idOccurrencesFromModels(models))

    def duplicateIdsFromService[B, C](service: ServiceModel[B, C]): List[DuplicateId] =
      duplicateIds(idOccurrencesFromService(service))

    def crossEntityDuplicateIds[A, B, C](
        modelSet: ModelSet[A],
        service: ServiceModel[B, C]
    ): List[DuplicateId] = {
      val occurrences =
        mergeIdOccurrences(
          idOccurrencesFromModels(modelSet.all),
          idOccurrencesFromService(service)
        )
      occurrences.toList
        .collect {
          case (id, counts) if counts.models > 0 && (counts.services > 0 || counts.operations > 0) =>
            DuplicateId(id, counts.models, counts.services, counts.operations)
        }
        .sortBy(_.id)
    }

    def cyclicAliasDefinitions[A](modelSet: ModelSet[A]): List[CyclicAliasDefinition] =
      modelSet.aliases.flatMap { alias =>
        aliasCycleFrom(alias.id, modelSet, Nil)
          .filter { cycle =>
            cycle.toList.min == alias.id
          }
          .map(CyclicAliasDefinition(_))
      }

    def unresolvedOperationRefs[A, B](
        modelSet: ModelSet[A],
        service: ServiceModel[?, B]
    ): List[UnresolvedModelRef] =
      service.operations
        .flatMap { operation =>
          val operationName = s"${operation.id.namespace}#${operation.id.name}"
          operation.input.toList.map { ref =>
            UnresolvedModelRef(ref, s"input of operation $operationName")
          } ++ operation.output.toList.map { ref =>
            UnresolvedModelRef(ref, s"output of operation $operationName")
          } ++ operation.errors.map { ref =>
            UnresolvedModelRef(ref, s"errors of operation $operationName")
          }
        }
        .filter { case UnresolvedModelRef(ref, _) => modelSet.resolve(ref).isEmpty }

    private def aliasCycleFrom[A](
        current: ModelId,
        modelSet: ModelSet[A],
        path: List[ModelId]
    ): Option[NonEmptyList[ModelId]] =
      if (path.contains(current)) {
        val cycleStart = path.indexOf(current)
        NonEmptyList.fromList(path.drop(cycleStart) :+ current)
      } else {
        modelSet.resolve(current).flatMap(_.asAlias) match {
          case Some(alias) =>
            alias.underlying match {
              case ModelRef(nextId) => aliasCycleFrom(nextId, modelSet, path :+ current)
              case _                => None
            }
          case None        => None
        }
      }
  }
}
