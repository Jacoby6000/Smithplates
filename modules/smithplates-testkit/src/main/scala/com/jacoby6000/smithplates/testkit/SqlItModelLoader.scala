package com.jacoby6000.smithplates.testkit

import software.amazon.smithy.model.Model

import java.io.InputStream
import java.nio.charset.StandardCharsets

object SqlItModelLoader {
  val SqlSchemaTraitsModelId: String  = "META-INF/smithy/smithplates.codegen.sql.smithy"
  val SqlServiceTraitsModelId: String = "META-INF/smithy/smithplates.codegen.sql.service.smithy"

  def assemble(additionalModels: (String, String)*): Model = {
    val assembler = assemblerWithSqlTraits
    additionalModels.foreach { case (id, content) => assembler.addUnparsedModel(id, content) }
    assembler.assemble().unwrap()
  }

  private def assemblerWithSqlTraits = {
    val assembler = Model.assembler().disableValidation()
    assembler.addUnparsedModel(SqlSchemaTraitsModelId, readClasspathResource(SqlSchemaTraitsModelId))
    assembler.addUnparsedModel(SqlServiceTraitsModelId, readClasspathResource(SqlServiceTraitsModelId))
    assembler
  }

  private def readClasspathResource(path: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse {
      throw new IllegalStateException(
        s"SQL traits Smithy model not on classpath at '$path'. " +
          "Ensure smithy-sql-ir and smithy-sql-service-ir are on the classpath."
      )
    }
    try
      readStream(stream)
    finally
      stream.close()
  }

  private def readStream(stream: InputStream): String =
    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
}
