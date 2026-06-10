package com.jacoby6000.smithplates.sql.service.renderer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SqlSchemaHash {
  def sha256Hex(content: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes  = digest.digest(content.getBytes(StandardCharsets.UTF_8))
    bytes.map("%02x".format(_)).mkString
  }
}
