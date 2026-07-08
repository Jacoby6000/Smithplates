package com.jacoby6000.smithplates.http

object HttpModelTypeNames {
  def referencedModelTypeNames(
      typeName: String,
      structureNames: Set[String],
      unionNames: Set[String],
      enumNames: Set[String] = Set.empty
  ): List[String] =
    internal.referencedModelTypeNamesRec(typeName, structureNames, unionNames, enumNames).toList.sorted

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def referencedModelTypeNamesRec(
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
  }
}
