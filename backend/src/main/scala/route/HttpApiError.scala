package route

import io.circe.Encoder
import org.http4s.Status

private[route] final case class HttpApiError(
  status: Status,
  code: String,
  message: String
)

private[route] final case class HttpApiErrorResponse(
  error: String,
  code: String
)

private[route] object HttpApiErrorResponse {
  given Encoder[HttpApiErrorResponse] =
    Encoder.forProduct2("error", "code")(response => (response.error, response.code))

  def fromError(error: HttpApiError): HttpApiErrorResponse =
    HttpApiErrorResponse(error = error.message, code = error.code)
}
