package slaydemo.backend.identity.routes

import slaydemo.backend.identity.api.{IdentityAccountSummary, IdentityAuthResponse}
import slaydemo.backend.identity.objects.{IdentityAccount, SkinId}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object IdentityRouteJsonRenderer {
  def authResponse(account: IdentityAccount): IdentityAuthResponse =
    IdentityAuthResponse(
      handle = account.handle.value,
      skinId = SkinId.wireValue(account.skinId),
      session = account.sessionToken.map(_.value).getOrElse("")
    )

  def renderAuth(response: IdentityAuthResponse): String =
    s"""{"handle":"${escape(response.handle)}","skinId":"${escape(response.skinId)}","session":"${escape(response.session)}"}"""

  def renderAccounts(accounts: Vector[IdentityAccountSummary]): String = {
    val renderedAccounts = accounts
      .map(account =>
        s"""{"handle":"${escape(account.handle)}","displayName":"${escape(account.displayName)}","skinId":"${escape(account.skinId)}"}"""
      )
      .mkString(",")
    s"""{"accounts":[$renderedAccounts]}"""
  }

  def renderError(code: String, message: String): String =
    s"""{"error":"${escape(message)}","code":"${escape(code)}"}"""

  private def escape(value: String): String =
    HttpRouteSupport.escapeJson(value)
}
