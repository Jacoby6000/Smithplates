package com.jacoby6000.smithplates.sql.service.renderer.codegentest

object TextContentDiff {
  def formatMismatch(
      fileLabel: String,
      expected: String,
      actual: String,
      contextLines: Int = internal.DefaultContextLines
  ): String = {
    val expectedLines = expected.linesIterator.toVector
    val actualLines   = actual.linesIterator.toVector

    if (expectedLines == actualLines) {
      return s"Content mismatch for $fileLabel (identical line sequences but unequal strings; check line endings)"
    }

    val firstDiffIndex = (0 until math.max(expectedLines.size, actualLines.size))
      .find { index =>
        expectedLines.lift(index) != actualLines.lift(index)
      }
      .getOrElse(0)

    val contextStart = math.max(0, firstDiffIndex - contextLines)
    val contextEnd   =
      math.min(
        math.max(expectedLines.size, actualLines.size),
        firstDiffIndex + contextLines + 1
      )

    val header =
      s"""Content mismatch for $fileLabel
         |First difference near line ${firstDiffIndex + 1}
         |--- expected
         |+++ actual
         |""".stripMargin

    val hunks = (contextStart until contextEnd).map { index =>
      val lineNumber   = index + 1
      val expectedLine = expectedLines.lift(index).map(line => s"    $lineNumber | - $line").getOrElse {
        s"    $lineNumber | - <missing>"
      }
      val actualLine   = actualLines.lift(index).map(line => s"    $lineNumber | + $line").getOrElse {
        s"    $lineNumber | + <missing>"
      }
      s"$expectedLine\n$actualLine"
    }

    header + hunks.mkString("\n")
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val DefaultContextLines = 5
  }
}
