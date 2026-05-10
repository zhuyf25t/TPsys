package slaydemo.backend.forum.routes

import slaydemo.backend.shared.json.{JsonObjectParseError, JsonObjectParser}

private[routes] object ForumRequestBodyParser {
  def parse(rawBody: String): Either[String, ForumRequestFields] =
    JsonObjectParser.parseNullableStringFields(rawBody) match {
      case Right(parsedFields) =>
        val stringFields = parsedFields.collect { case (name, Some(value)) => name -> value }
        parsedFields.get("vote") match {
          case Some(None) =>
            Right(ForumRequestFields(stringFields.updated("vote", ""), voteSeen = true))
          case Some(Some(value)) =>
            Right(ForumRequestFields(stringFields.updated("vote", value), voteSeen = true))
          case None =>
            Right(ForumRequestFields(stringFields, voteSeen = false))
        }
      case Left(JsonObjectParseError.ExpectedObject) =>
        Left("Request body must be a JSON object.")
      case Left(JsonObjectParseError.ExpectedStringField) =>
        Left("Request body must be a JSON object with string fields.")
    }
}

private[routes] final case class ForumRequestFields(fields: Map[String, String], voteSeen: Boolean) {
  def stringValue(name: String): String =
    fields.getOrElse(name, "")
}
