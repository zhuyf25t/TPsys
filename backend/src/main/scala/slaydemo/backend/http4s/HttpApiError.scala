package slaydemo.backend.http4s

import org.http4s.Status

private[http4s] final case class HttpApiError(
  status: Status,
  code: String,
  message: String
)
