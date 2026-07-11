package com.jacoby6000.smithplates.sql.service.renderer

import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.sql.service.core.SqlMeta
import com.jacoby6000.smithplates.sql.service.core.SqlOperationMeta
import com.jacoby6000.smithplates.sql.service.core.SqlServiceMeta

/** Neutral [[TemplateView]] helpers for SQL service-scoped SSP templates.
  *
  * The SQL db templates still consume the legacy [[ServiceTemplateView]] for fragment/includes compatibility. This
  * envelope carries the neutral `TemplateView` (planner-rendered) alongside the legacy view built from the
  * [[SqlCodegenServiceContext]] so service templates can be migrated incrementally without a dual-run bridge.
  */
object SqlNeutralServiceTemplateAttributes {
  type ServiceView = TemplateView[ServiceModel[SqlServiceMeta, SqlOperationMeta], SqlMeta]

  final case class ServiceEnvelope(
      view: ServiceView,
      data: ServiceTemplateView
  )

  def envelope(view: ServiceView, context: SqlCodegenServiceContext): ServiceEnvelope =
    ServiceEnvelope(view = view, data = SqlCodegenTemplateViews.buildServiceView(context))

  def templateData(envelope: ServiceEnvelope): ServiceTemplateView =
    envelope.data

  def serviceView(envelope: ServiceEnvelope): ServiceView =
    envelope.view
}
