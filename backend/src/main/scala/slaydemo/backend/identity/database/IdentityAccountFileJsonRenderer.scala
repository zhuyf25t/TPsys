package slaydemo.backend.identity.database

import slaydemo.backend.identity.objects.SkinId

private[database] object IdentityAccountFileJsonRenderer {
  def renderPayload(accounts: Vector[FileStoredIdentityAccount]): String = {
    val rendered = accounts.map(renderStoredAccount).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.identity-accounts.v1",
       |  "accounts": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderStoredAccount(stored: FileStoredIdentityAccount): String = {
    val account = stored.account
    s"""    {
       |      "userId": "${escape(account.userId.value)}",
       |      "handle": "${escape(account.handle.value)}",
       |      "displayName": "${escape(account.displayName.value)}",
       |      "skinId": "${escape(SkinId.wireValue(account.skinId))}",
       |      "sessionToken": "${escape(account.sessionToken.map(_.value).getOrElse(""))}",
       |      "active": ${stored.active},
       |      "password": "${escape(stored.passwordSecret)}"
       |    }""".stripMargin
  }

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
