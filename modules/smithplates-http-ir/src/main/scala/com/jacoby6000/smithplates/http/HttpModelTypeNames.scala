package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.HttpStructure
import com.jacoby6000.smithplates.http.model.HttpStructureMember
import com.jacoby6000.smithplates.http.model.HttpUnion
import com.jacoby6000.smithplates.http.model.HttpUnionMember

object HttpModelTypeNames {
  def referencedModelTypeNames(
      typeName: String,
      structureNames: Set[String],
      unionNames: Set[String],
      enumNames: Set[String] = Set.empty
  ): List[String] =
    referencedModelTypeNamesRec(typeName, structureNames, unionNames, enumNames).toList.sorted

  private def referencedModelTypeNamesRec(
      typeName: String,
      structureNames: Set[String],
      unionNames: Set[String],
      enumNames: Set[String]
  ): Set[String] =
    if (typeName.startsWith("List[")) {
      val inner = typeName.substring(5, typeName.length - 1)
      referencedModelTypeNamesRec(inner, structureNames, unionNames, enumNames)
    } else if (typeName.startsWith("Map[String, ")) {
      val inner = typeName.substring(12, typeName.length - 1)
      referencedModelTypeNamesRec(inner, structureNames, unionNames, enumNames)
    } else if (structureNames.contains(typeName) || unionNames.contains(typeName) || enumNames.contains(typeName)) {
      Set(typeName)
    } else {
      Set.empty
    }

  def structureReferencedTypeNames(
      structure: HttpStructure,
      serviceStructures: Set[String],
      serviceUnions: Set[String],
      serviceEnums: Set[String] = Set.empty
  ): List[String] =
    structure.members
      .flatMap(member => referencedModelTypeNames(member.typeName, serviceStructures, serviceUnions, serviceEnums))
      .filterNot(_ == structure.name)
      .distinct
      .sorted

  def unionReferencedTypeNames(
      union: HttpUnion,
      serviceStructures: Set[String],
      serviceUnions: Set[String],
      serviceEnums: Set[String] = Set.empty
  ): List[String] =
    union.members
      .flatMap(member => referencedModelTypeNames(member.typeName, serviceStructures, serviceUnions, serviceEnums))
      .filterNot(_ == union.name)
      .distinct
      .sorted

  def needsDatetimeImport(members: List[HttpStructureMember]): Boolean =
    members.exists(member => referencesTimestamp(member.typeName))

  def needsAnyImport(members: List[HttpStructureMember]): Boolean =
    members.exists(member => referencesDocument(member.typeName))

  def unionNeedsDatetimeImport(members: List[HttpUnionMember]): Boolean =
    members.exists(member => referencesTimestamp(member.typeName))

  private def referencesDocument(typeName: String): Boolean =
    if (typeName == "Document") {
      true
    } else if (typeName.startsWith("List[")) {
      referencesDocument(typeName.substring(5, typeName.length - 1))
    } else if (typeName.startsWith("Map[String, ")) {
      referencesDocument(typeName.substring(12, typeName.length - 1))
    } else {
      false
    }

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
