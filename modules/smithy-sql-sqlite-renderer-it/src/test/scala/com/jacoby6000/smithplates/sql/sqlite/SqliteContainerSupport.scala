package com.jacoby6000.smithplates.sql.sqlite

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.DockerImage
import com.jacoby6000.smithplates.sql.shared.DDLStatement

object SqliteContainerSupport {
  private val dbDirectory = "/tmp/sql-plugin-it"
  private val dbPath      = s"$dbDirectory/test.db"

  val containerDef: GenericContainer.Def[GenericContainer] =
    GenericContainer.Def(
      dockerImage = DockerImage(Left("keinos/sqlite3:latest")),
      command = Seq("tail", "-f", "/dev/null")
    )

  def applyDdl(container: GenericContainer, statements: Iterable[DDLStatement]): Unit = {
    container.container.execInContainer("mkdir", "-p", dbDirectory)
    statements.foreach { ddl =>
      val exitCode = runSqlScript(container, ddl.statement)
      if (exitCode != 0) {
        throw new IllegalStateException(
          s"sqlite3 schema apply failed (exit $exitCode) for statement:\n${ddl.statement}"
        )
      }
    }
  }

  def listTables(container: GenericContainer): Set[String] = {
    val result =
      runSqlScriptWithOutput(
        container,
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name;"
      )
    if (result.getExitCode != 0) {
      throw new IllegalStateException(s"sqlite3 list tables failed: ${result.getStderr}")
    }
    result.getStdout.linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .toSet
  }

  def execSql(container: GenericContainer, sql: String): Int =
    runSqlScript(container, sql)

  private def runSqlScript(container: GenericContainer, sql: String): Int =
    runSqlScriptWithOutput(container, sql).getExitCode

  private def runSqlScriptWithOutput(container: GenericContainer, sql: String) = {
    val script =
      s"""PRAGMA foreign_keys = ON;
         |$sql""".stripMargin
    container.container.execInContainer(
      "sh",
      "-c",
      s"sqlite3 $dbPath <<'SQLITESCRIPT'\n$script\nSQLITESCRIPT"
    )
  }
}
