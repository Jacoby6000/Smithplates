package com.jacoby6000.smithy.stache.sql

import java.io.InputStream
import java.nio.charset.StandardCharsets

import software.amazon.smithy.model.Model

object SqlTestModelLoader {
  val SqlTraitsModelId: String = "META-INF/smithy/stache.codegen.sql.smithy"

  def assemblerWithSqlTraits = {
    val traits = readClasspathResource(SqlTraitsModelId)
    Model.assembler().disableValidation().addUnparsedModel(SqlTraitsModelId, traits)
  }

  def assemble(additionalModels: (String, String)*): Model = {
    val assembler = assemblerWithSqlTraits
    additionalModels.foreach { case (id, content) => assembler.addUnparsedModel(id, content) }
    assembler.assemble().unwrap()
  }

  private def readClasspathResource(path: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse {
      throw new IllegalStateException(
        s"SQL traits Smithy model not on classpath at '$path'. " +
          "Ensure traits are packaged under smithy-plugins/sql-plugin/src/main/resources/META-INF/smithy/."
      )
    }
    try readStream(stream)
    finally stream.close()
  }

  private def readStream(stream: InputStream): String =
    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
}
