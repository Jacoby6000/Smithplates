package com.jacoby6000.smithplates.http

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler

import java.io.InputStream
import java.nio.charset.StandardCharsets

object HttpTestModelLoader {
  val AwsProtocolsModelId: String = "META-INF/smithy/aws.protocols.smithy"

  def assemblerWithAwsProtocols: ModelAssembler = {
    val assembler = Model.assembler().disableValidation()
    assembler.addUnparsedModel(AwsProtocolsModelId, readClasspathResource(AwsProtocolsModelId))
    assembler
  }

  def assemble(additionalModels: (String, String)*): Model = {
    val assembler = assemblerWithAwsProtocols
    additionalModels.foreach { case (id, content) => assembler.addUnparsedModel(id, content) }
    assembler.assemble().unwrap()
  }

  private def readClasspathResource(path: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse {
      throw new IllegalStateException(
        s"AWS protocol Smithy model not on classpath at '$path'. " +
          "Ensure smithy-aws-traits is on the classpath."
      )
    }
    try readStream(stream)
    finally stream.close()
  }

  private def readStream(stream: InputStream): String =
    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
}
