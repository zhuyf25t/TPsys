package slaydemo.backend.shared.api

final case class SharedEnvelope[A](
  traceId: String,
  payload: A
)

trait SharedEnvelopeApi
