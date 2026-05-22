package slaydemo.backend.http4s

import org.http4s.Status

private[http4s] object HttpApiErrors {
  private def apiError(status: Status, code: String, message: String): HttpApiError =
    HttpApiError(status, code, message)

  def typedApiError(statusCode: Int, code: String, message: String): HttpApiError =
    apiError(statusFrom(statusCode), code, message)

  private def statusFrom(statusCode: Int): Status =
    statusCode match {
      case 400 => Status.BadRequest
      case 401 => Status.Unauthorized
      case 403 => Status.Forbidden
      case 404 => Status.NotFound
      case 405 => Status.MethodNotAllowed
      case 409 => Status.Conflict
      case _   => Status.InternalServerError
    }
}
