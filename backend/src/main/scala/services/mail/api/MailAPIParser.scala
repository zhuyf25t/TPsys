package services.mail.api

object MailAPIParser {
  def listMessageFromQuery(query: Map[String, String]): MailListAPIMessage =
    MailListAPIMessage(
      ownerHandle = query.get("ownerHandle").flatMap(MailAPIMessageDecoding.playerHandleFromWire)
    )
}
