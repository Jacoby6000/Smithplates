package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpStructure
import com.jacoby6000.smithplates.http.model.HttpStructureMember
import com.jacoby6000.smithplates.http.model.HttpUnion
import com.jacoby6000.smithplates.http.model.HttpUnionMember

object HttpModelTypeNames {
  def referencedModelTypeNames(
      typeName: String,
      structureNames: Set[String],
      unionNames: Set[String]
  ): List[String] =
    referencedModelTypeNamesRec(typeName, structureNames, unionNames).toList.sorted

  private def referencedModelTypeNamesRec(
      typeName: String,
      structureNames: Set[String],
      unionNames: Set[String]
  ): Set[String] =
    if (typeName.startsWith("List[")) {
      val inner = typeName.substring(5, typeName.length - 1)
      referencedModelTypeNamesRec(inner, structureNames, unionNames)
    } else if (typeName.startsWith("Map[String, ")) {
      val inner = typeName.substring(12, typeName.length - 1)
      referencedModelTypeNamesRec(inner, structureNames, unionNames)
    } else if (structureNames.contains(typeName) || unionNames.contains(typeName)) {
      Set(typeName)
    } else {
      Set.empty
    }

  def structureReferencedTypeNames(
      structure: HttpStructure,
      serviceStructures: Set[String],
      serviceUnions: Set[String]): List[String] =
    structure.members
      .flatMap(member => referencedModelTypeNames(member.typeName, serviceStructures, serviceUnions))
      .filterNot(_ == structure.name)
      .distinct
      .sorted

  def unionReferencedTypeNames(
      union: HttpUnion,
      serviceStructures: Set[String],
      serviceUnions: Set[String]): List[String] =
    union.members
      .flatMap(member => referencedModelTypeNames(member.typeName, serviceStructures, serviceUnions))
      .filterNot(_ == union.name)
      .distinct
      .sorted

  def needsDatetimeImport(members: List[HttpStructureMember]): Boolean =
    members.exists(member => referencesTimestamp(member.typeName))

  def unionNeedsDatetimeImport(members: List[HttpUnionMember]): Boolean =
    members.exists(member => referencesTimestamp(member.typeName))

  private def referencesTimestamp(typeName: String): Boolean =
    if (typeName == "Timestamp") {
      true
    } else if (typeName.startsWith("List[")) {
      referencesTimestamp(typeName.substring(5, typeName.length - 1))
    } else if (typeName.startsWith("Map[String, ")) {
      referencesTimestamp(typeName.substring(12, typeName.length - 1))
    } else {
      false
    }
}
