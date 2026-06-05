package com.jacoby6000.smithy.sql.it

import java.io.InputStream
import java.nio.charset.StandardCharsets

import software.amazon.smithy.model.Model

object SqlItModelLoader {
  val SqlTraitsModelId: String = "META-INF/smithy/jacoby6000.codegen.sql.smithy"

  def assemble(additionalModels: (String, String)*): Model = {
    val assembler = assemblerWithSqlTraits
    additionalModels.foreach { case (id, content) => assembler.addUnparsedModel(id, content) }
    assembler.assemble().unwrap()
  }

  private def assemblerWithSqlTraits = {
    val traits = readClasspathResource(SqlTraitsModelId)
    Model.assembler().disableValidation().addUnparsedModel(SqlTraitsModelId, traits)
  }

  private def readClasspathResource(path: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse {
      throw new IllegalStateException(
        s"SQL traits Smithy model not on classpath at '$path'. " +
          "Ensure smithy-stache-plugin is on the classpath."
      )
    }
    try {
      readStream(stream)
    } finally {
      stream.close()
    }
  }

  private def readStream(stream: InputStream): String =
    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
}
