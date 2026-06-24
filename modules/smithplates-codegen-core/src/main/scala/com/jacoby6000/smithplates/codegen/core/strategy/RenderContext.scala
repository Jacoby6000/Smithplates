package com.jacoby6000.smithplates.codegen.core.strategy

import com.jacoby6000.smithplates.codegen.core.TypeResolver

/** Per-render context passed to [[TypeRenderer]] implementations. */
final case class RenderContext[A](
    typeResolver: TypeResolver[A],
    conventions: Conventions
)
