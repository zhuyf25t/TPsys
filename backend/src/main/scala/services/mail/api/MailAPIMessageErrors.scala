package services.mail.api

import cats.effect.IO

import services.mail.objects.MailRecord
import services.mail.services.MailReadError
import system.api.APIMessageError

private[api] object MailAPIMessageErrors {
  def owner(error: MailRouteOwnerError): APIMessageError =
    MailAPIMessageSupport.error(MailApiErrorCode.fromOwnerError(error))

  def readRoute(error: MailRouteReadError): APIMessageError =
    MailAPIMessageSupport.error(MailApiErrorCode.fromReadError(error))

  def markRead(result: Either[MailReadError, MailRecord]): IO[MailReadResponse] =
    result.fold(
      {
        case MailReadError.MailNotFound =>
          IO.raiseError(MailAPIMessageSupport.error(MailApiErrorCode.MailNotFound))
      },
      _ => IO.pure(MailReadResponse.Read)
    )
}
