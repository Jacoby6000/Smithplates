package com.jacoby6000.smithplates.sql

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler

import java.io.InputStream
import java.nio.charset.StandardCharsets

object SqlTestModelLoader {
  val SqlSchemaTraitsModelId: String  = "META-INF/smithy/smithplates.codegen.sql.smithy"
  val SqlServiceTraitsModelId: String = "META-INF/smithy/smithplates.codegen.sql.service.smithy"

  def assemblerWithSqlTraits = {
    val assembler = Model.assembler().disableValidation()
    assembler.addUnparsedModel(SqlSchemaTraitsModelId, readClasspathResource(SqlSchemaTraitsModelId))
    addServiceTraitsIfPresent(assembler)
    assembler
  }

  def assemble(additionalModels: (String, String)*): Model = {
    val assembler = assemblerWithSqlTraits
    additionalModels.foreach { case (id, content) => assembler.addUnparsedModel(id, content) }
    assembler.assemble().unwrap()
  }

  private def addServiceTraitsIfPresent(assembler: ModelAssembler): Unit =
    Option(getClass.getClassLoader.getResourceAsStream(SqlServiceTraitsModelId)).foreach { stream =>
      try
        assembler.addUnparsedModel(SqlServiceTraitsModelId, readStream(stream))
      finally
        stream.close()
    }

  private def readClasspathResource(path: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse {
      throw new IllegalStateException(
        s"SQL schema traits Smithy model not on classpath at '$path'. " +
          "Ensure smithplates-sql-ir is on the classpath."
      )
    }
    try readStream(stream)
    finally stream.close()
  }

  private def readStream(stream: InputStream): String =
    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
}
