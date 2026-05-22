package services.bots.database

import services.bots.objects.{BotProfileRecord, BotProfileTone}

private[database] object BotProfileFileJsonRenderer {
  def renderPayload(records: Vector[BotProfileRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.bot-profiles.v1",
       |  "profiles": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: BotProfileRecord): String =
    s"""    {
       |      "botId": "${escape(record.botId.value)}",
       |      "handle": "${escape(record.handle.value)}",
       |      "displayName": "${escape(record.displayName.value)}",
       |      "initialRating": ${record.initialRating.value},
       |      "profileTone": "${escape(BotProfileTone.wireValue(record.profileTone))}",
       |      "strategyLabel": "${escape(record.strategyLabel.value)}",
       |      "profileOrder": ${record.profileOrder.value},
       |      "skin": {
       |        "avatarKey": "${escape(record.skin.avatarKey.value)}",
       |        "textureKey": "${escape(record.skin.textureKey.value)}",
       |        "label": "${escape(record.skin.label.value)}"
       |      }
       |    }""".stripMargin

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
