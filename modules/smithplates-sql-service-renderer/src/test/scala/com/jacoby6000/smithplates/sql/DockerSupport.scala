package com.jacoby6000.smithplates.sql.codegen

import scala.sys.process.*

object DockerSupport {
  def isAvailable: Boolean =
    Process(Seq("docker", "info")).!(
      ProcessLogger(_ => (), _ => ())
    ) == 0
}
