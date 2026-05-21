package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response}

import slaydemo.backend.http4s.Http4sRouteSupport.{blocking, corsNoContent, decodeEntityBody, errorResponse, requestPath, typedApiError, withCors}
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
      case request if MailRequestTarget.isListPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            listMails(request, service)
          case _ =>
            errorResponse(mailApiError(MailApiErrorCode.MethodNotAllowed))
        }
      case request if MailRequestTarget.isReadPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            markRead(request, service)
          case _ =>
            errorResponse(mailApiError(MailApiErrorCode.MethodNotAllowed))
        }
    }

  private def listMails(request: Request[IO], service: MailService): IO[Response[IO]] =
    MailOwnerQuery.parseFromQuery(request.params) match {
      case Left(error) =>
        errorResponse(ownerApiError(error))
      case Right(ownerHandle) =>
        blocking(service.list(ownerHandle)).flatMap(records =>
          Ok(MailListResponse.fromRecords(records).asJson).map(withCors)
        )
    }

  private def markRead(request: Request[IO], service: MailService): IO[Response[IO]] =
    readReadRequest(request).flatMap {
      case Left(MailReadApiRequestDecodeError.InvalidJsonObject) =>
        errorResponse(mailApiError(MailApiErrorCode.InvalidJsonObject))
      case Right(readRequest) =>
        readRequest.toCommand match {
          case Left(error) =>
            errorResponse(readApiError(error))
          case Right(command) =>
            blocking(service.markRead(command.ownerHandle, command.mailId)).flatMap {
              case Right(_) =>
                Ok(MailReadResponse(ok = true).asJson).map(withCors)
              case Left(MailReadError.MailNotFound) =>
                errorResponse(mailApiError(MailApiErrorCode.MailNotFound))
            }
        }
    }

  private def readReadRequest(request: Request[IO]): IO[Either[MailReadApiRequestDecodeError, MailReadApiRequest]] =
    decodeEntityBody[MailReadApiRequestDecodeError, MailReadApiRequest](
      request,
      MailReadApiRequestDecodeError.InvalidJsonObject
    )

  private def ownerApiError(error: MailRouteOwnerError): HttpApiError =
    mailApiError(MailApiErrorCode.fromOwnerError(error))

  private def readApiError(error: MailRouteReadError): HttpApiError =
    mailApiError(MailApiErrorCode.fromReadError(error))

  private def mailApiError(code: MailApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = MailApiErrorCode.statusCode(code),
      code = MailApiErrorCode.wireValue(code),
      message = MailApiErrorCode.message(code)
    )

}
