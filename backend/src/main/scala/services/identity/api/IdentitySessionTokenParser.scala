package services.identity.api

import services.identity.objects.SessionToken

object IdentitySessionTokenParser {
  private val AuthorizationHeaderName: String =
    "Authorization"
  private val SessionTokenHeaderName: String =
    "X-Session-Token"

  def parseFromHeaderLookup(headerValue: String => Option[String]): Option[SessionToken] =
    parse(
      authorization = headerValue(AuthorizationHeaderName),
      xSessionToken = headerValue(SessionTokenHeaderName)
    )

  def parse(
    authorization: Option[String],
    xSessionToken: Option[String]
  ): Option[SessionToken] =
    parseAuthorization(authorization).orElse(xSessionToken.flatMap(SessionToken.fromString))

  private def parseAuthorization(value: Option[String]): Option[SessionToken] =
    value.map(_.trim).filter(_.nonEmpty).flatMap { header =>
      header.split("\\s+", 2).toList match {
        case method :: token :: Nil if method.equalsIgnoreCase("Bearer") => SessionToken.fromString(token)
        case token :: Nil                                                => SessionToken.fromString(token)
        case _                                                           => None
      }
    }
}
