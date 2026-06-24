package com.jacoby6000.smithplates.codegen.core.strategy

import com.jacoby6000.smithplates.codegen.core.NeutralType

/** Renders [[NeutralType]] values into target-language type syntax. */
trait TypeRenderer {
  def render(tpe: NeutralType, ctx: RenderContext[?]): String
}
