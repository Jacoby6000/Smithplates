package com.jacoby6000.smithplates.plugin.codegentest

import org.apache.logging.log4j.LogManager

/** Progress logging for golden template Smithy builds (see `src/test/resources/log4j2.xml`). */
object TemplateBuildLog {
  val LoggerName = "com.jacoby6000.smithplates.plugin.codegentest.template-build"

  def phase(label: String, message: String, startedAt: Long): Unit = {
    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
    internal.logger.info(s"build - $label: $message (${elapsedMs}ms)")
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val logger = LogManager.getLogger(LoggerName)
  }
}
