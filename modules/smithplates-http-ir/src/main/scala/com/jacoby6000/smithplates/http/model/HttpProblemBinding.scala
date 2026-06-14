package com.jacoby6000.smithplates.http.model

final case class HttpProblemBinding(
    problemType: String,
    title: String,
    defaultDetail: Option[String]
)
