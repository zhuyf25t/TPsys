package slaydemo.backend.http4s

import io.circe.Encoder
import org.http4s.Status

private[http4s] final case class HttpApiError(
  status: Status,
  code: String,
  message: String
)

private[http4s] final case class HttpApiErrorResponse(
  error: String,
  code: String
)

private[http4s] object HttpApiErrorResponse {
  given Encoder[HttpApiErrorResponse] =
    Encoder.forProduct2("error", "code")(response => (response.error, response.code))

  def fromError(error: HttpApiError): HttpApiErrorResponse =
    HttpApiErrorResponse(error = error.message, code = error.code)
}
