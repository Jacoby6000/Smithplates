package com.jacoby6000.smithplates.plugin.codegentest

import munit.FunSuite

import java.nio.file.Path
import java.nio.file.Paths

class ConsumerCodegenBuildSpec extends FunSuite {
  test("Smithy build fails when appended consumer output collides on resolved path") {
    val caseDirectory =
      ConsumerCodegenBuildSpec.internal.repoRoot.resolve(
        "templates/python/tests/http-additional-templates-path-collision"
      )
    SmithyBuildTemplateRunner.run(caseDirectory, getClass.getClassLoader) match {
      case Left(message) =>
        assert(message.contains("Duplicate resolved output path"))
      case Right(_)      =>
        fail("expected Smithy build to fail on duplicate resolved output path")
    }
  }
}

object ConsumerCodegenBuildSpec {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    lazy val repoRoot: Path =
      Paths.get(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize
  }
}
