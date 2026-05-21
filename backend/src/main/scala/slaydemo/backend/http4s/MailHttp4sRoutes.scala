package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}
import slaydemo.backend.mail.objects.apiTypes.{
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
  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Method is not allowed.")
  private val MissingOwnerError =
    HttpApiError(status = Status.BadRequest, code = "missing_owner", message = "missing_owner")
  private val VisitorNotAllowedError =
    HttpApiError(status = Status.Forbidden, code = "visitor_not_allowed", message = "visitor_not_allowed")
  private val InvalidOwnerError =
    HttpApiError(status = Status.BadRequest, code = "invalid_owner", message = "invalid_owner")
  private val MissingMailIdError =
    HttpApiError(status = Status.BadRequest, code = "missing_mail_id", message = "missing_mail_id")
  private val MailNotFoundError =
    HttpApiError(status = Status.NotFound, code = "mail_not_found", message = "mail_not_found")
  private val InvalidJsonObjectError =
    HttpApiError(status = Status.BadRequest, code = "bad_request", message = "Request body must be a JSON object with string fields.")

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
            IO.pure(apiError(MethodNotAllowedError))
        }
      case request if MailRequestTarget.isReadPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            markRead(request, service)
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
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
        IO.pure(apiError(InvalidJsonObjectError))
      case Right(readRequest) =>
        readRequest.toCommand match {
          case Left(error) =>
            IO.pure(apiError(readApiError(error)))
          case Right(command) =>
            blocking(service.markRead(command.ownerHandle, command.mailId)).flatMap {
              case Right(_) =>
                Ok(MailReadResponse(ok = true).asJson).map(withCors)
              case Left(MailReadError.MailNotFound) =>
                IO.pure(apiError(MailNotFoundError))
            }
        }
    }

  private def readReadRequest(request: Request[IO]): IO[Either[MailReadApiRequestDecodeError, MailReadApiRequest]] =
    request
      .as[MailReadApiRequest]
      .attempt
      .map(_.left.map(_ => MailReadApiRequestDecodeError.InvalidJsonObject))

  private def ownerApiError(error: MailRouteOwnerError): HttpApiError =
    error match {
      case MailRouteOwnerError.MissingOwner      => MissingOwnerError
      case MailRouteOwnerError.VisitorNotAllowed => VisitorNotAllowedError
      case MailRouteOwnerError.InvalidOwner      => InvalidOwnerError
    }

  private def readApiError(error: MailRouteReadError): HttpApiError =
    error match {
      case MailRouteReadError.MissingOwner      => MissingOwnerError
      case MailRouteReadError.VisitorNotAllowed => VisitorNotAllowedError
      case MailRouteReadError.InvalidOwner      => InvalidOwnerError
      case MailRouteReadError.MissingMailId     => MissingMailIdError
    }

  private def path(request: Request[IO]): String =
    request.uri.path.renderString

}
