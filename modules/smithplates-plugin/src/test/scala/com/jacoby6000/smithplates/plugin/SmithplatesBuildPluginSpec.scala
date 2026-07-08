package com.jacoby6000.smithplates.plugin

import munit.FunSuite

import java.util.logging.*
import scala.jdk.CollectionConverters.*

class SmithplatesBuildPluginSpec extends FunSuite {
  private val logger = SmithplatesBuildPlugin.internal.logger

  test("warnWhenExternalTemplatesEnabled emits warning when flag is enabled") {
    val handler = new TestLogHandler
    withLogger(handler) {
      SmithplatesBuildPlugin.internal.warnWhenExternalTemplatesEnabled(
        externalTemplatesSettings(enabled = true)
      )
      assert(handler.records.asScala.exists(_.getMessage.contains("enableExternalTemplates")))
    }
  }

  test("warnWhenExternalTemplatesEnabled stays silent when flag is disabled") {
    val handler = new TestLogHandler
    withLogger(handler) {
      SmithplatesBuildPlugin.internal.warnWhenExternalTemplatesEnabled(
        externalTemplatesSettings(enabled = false)
      )
      assertEquals(handler.records.asScala.filter(_.getLevel == Level.WARNING).toList, Nil)
    }
  }

  private def externalTemplatesSettings(enabled: Boolean): SmithplatesSettings =
    SmithplatesSettings
      .parseJson(s"""
        {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "enableExternalTemplates": $enabled,
            "http": {
              "server": {}
            }
          }
        }
      """)
      .fold(errors => fail(errors.map(_.message).toList.mkString("; ")), identity)

  private def withLogger(handler: Handler)(body: => Unit): Unit = {
    val previousLevel             = logger.getLevel
    val previousUseParentHandlers = logger.getUseParentHandlers
    logger.addHandler(handler)
    logger.setLevel(Level.WARNING)
    logger.setUseParentHandlers(false)
    try body
    finally {
      logger.removeHandler(handler)
      logger.setLevel(previousLevel)
      logger.setUseParentHandlers(previousUseParentHandlers)
      handler.close()
    }
  }
}

final private class TestLogHandler extends Handler {
  val records                                   = new java.util.concurrent.CopyOnWriteArrayList[LogRecord]()
  override def publish(record: LogRecord): Unit = { records.add(record); () }
  override def flush(): Unit                    = ()
  override def close(): Unit                    = ()
}
