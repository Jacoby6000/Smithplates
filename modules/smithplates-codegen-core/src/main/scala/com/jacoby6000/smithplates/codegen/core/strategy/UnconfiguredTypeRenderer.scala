package com.jacoby6000.smithplates.codegen.core.strategy

import com.jacoby6000.smithplates.codegen.core.NeutralType

/** Sentinel TypeRenderer that throws if used, to catch missing wiring early. */
object UnconfiguredTypeRenderer extends TypeRenderer {
  def render(tpe: NeutralType, ctx: RenderContext[?]): String =
    throw new IllegalStateException(
      s"UnconfiguredTypeRenderer: no TypeRenderer wired into CodegenSettings (rendering $tpe)"
    )
}
