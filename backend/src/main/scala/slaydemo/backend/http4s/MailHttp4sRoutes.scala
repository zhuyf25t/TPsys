package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}
import slaydemo.backend.mail.objects.apiTypes.{
  MailApiErrorCode,
  MailListResponse,
  MailOwnerQuery,
  MailReadApiRequest,
  MailReadApiRequestDecodeError,
  MailReadResponse,
  MailRequestTarget,
  MailRouteOwnerError,
  MailRouteReadError
}
import slaydemo.backend.mail.services.{MailReadError, MailService}

private[http4s] object MailHttp4sRoutes {
  import CirceEntityDecoder.*
  import CirceEntityEncoder.*

  def routes(service: MailService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if MailRequestTarget.isListPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            listMails(request, service)
          case _ =>
            IO.pure(apiError(mailApiError(MailApiErrorCode.MethodNotAllowed)))
        }
      case request if MailRequestTarget.isReadPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            markRead(request, service)
          case _ =>
            IO.pure(apiError(mailApiError(MailApiErrorCode.MethodNotAllowed)))
        }
    }

  private def listMails(request: Request[IO], service: MailService): IO[Response[IO]] =
    MailOwnerQuery.parseFromQuery(request.params) match {
      case Left(error) =>
        IO.pure(apiError(ownerApiError(error)))
      case Right(ownerHandle) =>
        blocking(service.list(ownerHandle)).flatMap(records =>
          Ok(MailListResponse.fromRecords(records).asJson).map(withCors)
        )
    }

  private def markRead(request: Request[IO], service: MailService): IO[Response[IO]] =
    readReadRequest(request).flatMap {
      case Left(MailReadApiRequestDecodeError.InvalidJsonObject) =>
        IO.pure(apiError(mailApiError(MailApiErrorCode.InvalidJsonObject)))
      case Right(readRequest) =>
        readRequest.toCommand match {
          case Left(error) =>
            IO.pure(apiError(readApiError(error)))
          case Right(command) =>
            blocking(service.markRead(command.ownerHandle, command.mailId)).flatMap {
              case Right(_) =>
                Ok(MailReadResponse(ok = true).asJson).map(withCors)
              case Left(MailReadError.MailNotFound) =>
                IO.pure(apiError(mailApiError(MailApiErrorCode.MailNotFound)))
            }
        }
    }

  private def readReadRequest(request: Request[IO]): IO[Either[MailReadApiRequestDecodeError, MailReadApiRequest]] =
    request
      .as[MailReadApiRequest]
      .attempt
      .map(_.left.map(_ => MailReadApiRequestDecodeError.InvalidJsonObject))

  private def ownerApiError(error: MailRouteOwnerError): HttpApiError =
    mailApiError(MailApiErrorCode.fromOwnerError(error))

  private def readApiError(error: MailRouteReadError): HttpApiError =
    mailApiError(MailApiErrorCode.fromReadError(error))

  private def mailApiError(code: MailApiErrorCode): HttpApiError =
    HttpApiError(
      status = statusFrom(MailApiErrorCode.statusCode(code)),
      code = MailApiErrorCode.wireValue(code),
      message = MailApiErrorCode.message(code)
    )

  private def statusFrom(value: Int): Status =
    value match {
      case 400 => Status.BadRequest
      case 403 => Status.Forbidden
      case 404 => Status.NotFound
      case 405 => Status.MethodNotAllowed
      case _   => Status.InternalServerError
    }

  private def path(request: Request[IO]): String =
    request.uri.path.renderString

}
