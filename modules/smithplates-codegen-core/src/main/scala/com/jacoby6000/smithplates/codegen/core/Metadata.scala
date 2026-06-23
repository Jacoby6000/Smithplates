package com.jacoby6000.smithplates.codegen.core

final case class ModelMeta[A](documentation: Option[String], tags: List[String], feature: A)

final case class ServiceMeta[A](documentation: Option[String], tags: List[String], feature: A)

final case class OperationMeta[A](documentation: Option[String], tags: List[String], feature: A)
