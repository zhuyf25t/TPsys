package services.forum.api

import io.circe.Decoder

private[api] object ForumAPIMessageDecoding {
  import services.forum.objects.{ForumBody, ForumReplyId, ForumTag, ForumTitle, ForumTopicId}

  given forumTitleDecoder: Decoder[ForumTitle] =
    Decoder.decodeString.map(ForumTitle.apply)

  given forumBodyDecoder: Decoder[ForumBody] =
    Decoder.decodeString.map(ForumBody.apply)

  given forumTagDecoder: Decoder[ForumTag] =
    Decoder.decodeString.map(ForumTag.apply)

  given forumTopicIdOptionDecoder: Decoder[Option[ForumTopicId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(ForumTopicId.apply))

  given forumReplyIdOptionDecoder: Decoder[Option[ForumReplyId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(ForumReplyId.apply))

  given forumAuthorInputOptionDecoder: Decoder[Option[ForumAuthorInput]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.map(value => ForumAuthorInput.fromWire(Some(value))))

  given forumViewerHandleInputOptionDecoder: Decoder[Option[ForumViewerHandleInput]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.map(value => ForumViewerHandleInput.fromWire(Some(value))))

  given forumVoteInputDecoder: Decoder[ForumVoteInput] =
    Decoder.decodeString.map(value => ForumVoteInput.fromWire(Some(value)))

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
