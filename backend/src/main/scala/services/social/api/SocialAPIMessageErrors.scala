package services.social.api

import cats.effect.IO

import services.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestSubmissionResult
}
import system.api.APIMessageError

private[api] object SocialAPIMessageErrors {
  def owner(error: SocialRouteHandleError): APIMessageError =
    SocialAPIMessageSupport.error(SocialApiErrorCode.fromOwnerError(error))

  def createRoute(error: SocialRouteCreateError): APIMessageError =
    SocialAPIMessageSupport.error(SocialApiErrorCode.fromCreateRouteError(error))

  def respondRoute(error: SocialRouteRespondError): APIMessageError =
    SocialAPIMessageSupport.error(SocialApiErrorCode.fromRespondRouteError(error))

  def createService(
    result: Either[FriendRequestCreateError, FriendRequestSubmissionResult]
  ): IO[FriendRequestSubmissionResult] =
    result.fold(
      error => IO.raiseError(SocialAPIMessageSupport.error(SocialApiErrorCode.fromCreateServiceError(error))),
      IO.pure
    )

  def respondService(
    result: Either[FriendRequestRespondError, FriendRequestResponseResult]
  ): IO[FriendRequestResponseResult] =
    result.fold(
      error => IO.raiseError(SocialAPIMessageSupport.error(SocialApiErrorCode.fromRespondServiceError(error))),
      IO.pure
    )
}
