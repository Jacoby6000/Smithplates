package com.jacoby6000.smithy.stache.sql.postgres

import java.sql.Connection

object PostgresDdlSupport {
  def listTables(connection: Connection): Set[String] = {
    val statement = connection.createStatement()
    try {
      val resultSet =
        statement.executeQuery(
          """SELECT table_name
            |FROM information_schema.tables
            |WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            |ORDER BY table_name""".stripMargin
        )
      val tables    = scala.collection.mutable.ListBuffer.empty[String]
      while (resultSet.next())
        tables += resultSet.getString(1)
      tables.toSet
    } finally statement.close()
  }

  def countForeignKeys(connection: Connection): Int = {
    val statement = connection.createStatement()
    try {
      val resultSet =
        statement.executeQuery(
          """SELECT count(*)
            |FROM information_schema.table_constraints
            |WHERE table_schema = 'public' AND constraint_type = 'FOREIGN KEY'""".stripMargin
        )
      resultSet.next()
      resultSet.getInt(1)
    } finally statement.close()
  }
}
