package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.{Request, Status}

private[http4s] object Http4sRouteSupport {
  def blocking[A](thunk: => A): IO[A] =
    IO.blocking(thunk)

  def requestPath(request: Request[IO]): String =
    request.uri.path.renderString

  def apiError(status: Status, code: String, message: String): HttpApiError =
    HttpApiError(status = status, code = code, message = message)

  def typedApiError(statusCode: Int, code: String, message: String): HttpApiError =
    apiError(status = statusFrom(statusCode), code = code, message = message)

  def methodNotAllowedError(message: String): HttpApiError =
    typedApiError(statusCode = 405, code = "method_not_allowed", message = message)

  def codeMessageError(statusCode: Int, code: String): HttpApiError =
    typedApiError(statusCode = statusCode, code = code, message = code)

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
