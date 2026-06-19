package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.http.HttpModelTypeNames
import com.jacoby6000.smithplates.http.HttpProblemBindingExtractor
import com.jacoby6000.smithplates.http.model.HttpProblemBinding
import com.jacoby6000.smithplates.http.model.HttpService
import com.jacoby6000.smithplates.http.model.HttpStructure
import com.jacoby6000.smithplates.http.model.HttpStructureMember
import software.amazon.smithy.model.Model

object HttpStructureModelTemplateAttributes {
  final case class View(
      structure: HttpStructure,
      members: List[HttpStructureMember],
      problemBinding: Option[HttpProblemBinding],
      importTypeNames: List[String],
      needsDatetimeImport: Boolean,
      needsAnyImport: Boolean,
      packageName: String
  )

  def build(
      model: Model,
      service: HttpService,
      structure: HttpStructure,
      enumNames: Set[String],
      packageName: String
  ): View = {
    val problemBinding  = HttpProblemBindingExtractor.extract(model, structure.shapeId)
    val members         = problemBinding match {
      case Some(_) => internal.flattenedProblemPayloadMembers(service, structure).getOrElse(structure.members)
      case None    => structure.members
    }
    val structureNames  = service.structures.map(_.name).toSet
    val unionNames      = service.unions.map(_.name).toSet
    val importTypeNames =
      HttpModelTypeNames
        .structureReferencedTypeNames(
          structure.copy(members = members),
          structureNames,
          unionNames,
          enumNames
        )
        .filterNot(typeName => problemBinding.isDefined && typeName == "Problem")
    View(
      structure = structure,
      members = members,
      problemBinding = problemBinding,
      importTypeNames = importTypeNames,
      needsDatetimeImport = HttpModelTypeNames.needsDatetimeImport(members),
      needsAnyImport = HttpModelTypeNames.needsAnyImport(members),
      packageName = packageName
    )
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def flattenedProblemPayloadMembers(
        service: HttpService,
        structure: HttpStructure
    ): Option[List[HttpStructureMember]] =
      structure.members match {
        case List(single) if single.typeName == "Problem" =>
          service.structures.find(_.name == "Problem").map(_.members)
        case _                                            =>
          None
      }
  }
}
