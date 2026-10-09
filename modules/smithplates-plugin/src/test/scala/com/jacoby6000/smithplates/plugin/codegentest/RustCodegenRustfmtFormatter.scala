package com.jacoby6000.smithplates.plugin.codegentest

import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import scala.sys.process.*

/** Test-only formatting, matching the Python golden formatter; consumers need no formatter at generation time. */
object RustCodegenRustfmtFormatter {
  def format(output: Path, repoRoot: Path): Unit = {
    val paths = Files.walk(output)
    val files =
      try
        paths.iterator().asScala.filter(path => Files.isRegularFile(path) && path.toString.endsWith(".rs")).toList
      finally
        paths.close()
    if (files.nonEmpty) {
      val command = Seq("rustfmt", "--edition", "2024") ++ files.map(_.toAbsolutePath.toString)
      val exit    = Process(command, repoRoot.resolve("language-test-harnesses/rust").toFile).!
      require(exit == 0, s"rustfmt failed with exit code $exit")
    }
  }
}
