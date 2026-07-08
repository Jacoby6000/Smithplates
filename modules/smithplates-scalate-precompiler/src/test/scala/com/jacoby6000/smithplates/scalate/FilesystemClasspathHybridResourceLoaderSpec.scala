package com.jacoby6000.smithplates.scalate

import munit.FunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class FilesystemClasspathHybridResourceLoaderSpec extends FunSuite {
  test("resource loads consumer SSP from filesystem and fragments from classpath root") {
    val consumerTemplate = Files.createTempFile("consumer-template", ".ssp")
    Files.writeString(consumerTemplate, "# consumer body", StandardCharsets.UTF_8)
    try {
      val loader   =
        new FilesystemClasspathHybridResourceLoader(
          classLoader = getClass.getClassLoader,
          classpathTemplateRoot = "python/src/http/server",
          preamble = "// preamble\n",
          filesystemTemplate = consumerTemplate
        )
      val resource =
        loader
          .resource(consumerTemplate.getFileName.toString)
          .getOrElse(fail("expected consumer template resource"))
      assertEquals(resource.text, "// preamble\n# consumer body")
      assert(
        loader
          .resource("support_fragment.ssp")
          .exists(_.text.contains("classpath fragment"))
      )
    } finally {
      val _ = consumerTemplate.toFile.delete()
    }
  }

  test("resource returns none when filesystem template is missing") {
    val missingTemplate = Files.createTempDirectory("missing-consumer-template").resolve("missing.ssp")
    val loader          =
      new FilesystemClasspathHybridResourceLoader(
        classLoader = getClass.getClassLoader,
        classpathTemplateRoot = "python/src/http/server",
        preamble = "",
        filesystemTemplate = missingTemplate
      )
    assertEquals(loader.resource("missing.ssp"), None)
  }
}
