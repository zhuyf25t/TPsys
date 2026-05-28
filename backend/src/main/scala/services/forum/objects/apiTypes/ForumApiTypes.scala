package services.forum.objects.apiTypes

import io.circe.{Decoder, Encoder}

import services.forum.objects.{ForumReplyView, ForumTopicView, ForumVoteChoice}

enum ForumApiRequestDecodeError {
  case InvalidJsonObject
}

enum ForumVoteFieldPresence {
  case Missing
  case Present
}

final case class ForumApiRequestFields(fields: Map[String, String], votePresence: ForumVoteFieldPresence)

object ForumApiRequestFields {
  given Decoder[ForumApiRequestFields] =
    Decoder.decodeMap[String, Option[String]].map { parsedFields =>
      val stringFields = parsedFields.collect { case (name, Some(value)) => name -> value }
      parsedFields.get("vote") match {
        case Some(None) =>
          ForumApiRequestFields(stringFields.updated("vote", ""), ForumVoteFieldPresence.Present)
        case Some(Some(value)) =>
          ForumApiRequestFields(stringFields.updated("vote", value), ForumVoteFieldPresence.Present)
        case None =>
          ForumApiRequestFields(stringFields, ForumVoteFieldPresence.Missing)
      }
    }
}

final case class ForumReplyResponse(
  id: String,
  author: String,
  body: String,
  publishedAt: Long,
  viewerVote: Option[String],
  score: Int
)

object ForumReplyResponse {
  given Encoder[ForumReplyResponse] =
    Encoder.forProduct6("id", "author", "body", "publishedAt", "viewerVote", "score")(value =>
      (value.id, value.author, value.body, value.publishedAt, value.viewerVote, value.score)
    )

  def fromView(view: ForumReplyView): ForumReplyResponse =
    ForumReplyResponse(
      id = view.id.value,
      author = view.author.value,
      body = view.body.value,
      publishedAt = view.publishedAt.value,
      viewerVote = view.viewerVote.map(ForumVoteChoice.wireValue),
      score = view.score.value
    )
}

final case class ForumTopicResponse(
  id: String,
  title: String,
  author: String,
  excerpt: String,
  tag: String,
  replies: Int,
  updatedAt: Long,
  createdAt: Long,
  body: String,
  replyItems: Vector[ForumReplyResponse],
  viewerVote: Option[String],
  score: Int
)

object ForumTopicResponse {
  given Encoder[ForumTopicResponse] =
    Encoder.forProduct12(
      "id",
      "title",
      "author",
      "excerpt",
      "tag",
      "replies",
      "updatedAt",
      "createdAt",
      "body",
      "replyItems",
      "viewerVote",
      "score"
    )(value =>
      (
        value.id,
        value.title,
        value.author,
        value.excerpt,
        value.tag,
        value.replies,
        value.updatedAt,
        value.createdAt,
        value.body,
        value.replyItems,
        value.viewerVote,
        value.score
      )
    )

  def fromView(view: ForumTopicView): ForumTopicResponse =
    ForumTopicResponse(
      id = view.id.value,
      title = view.title.value,
      author = view.author.value,
      excerpt = view.excerpt,
      tag = view.tag.value,
      replies = view.replies.value,
      updatedAt = view.updatedAt.value,
      createdAt = view.createdAt.value,
      body = view.body.value,
      replyItems = view.replyItems.map(ForumReplyResponse.fromView),
      viewerVote = view.viewerVote.map(ForumVoteChoice.wireValue),
      score = view.score.value
    )
}

final case class ForumTopicListResponse(topics: Vector[ForumTopicResponse])

object ForumTopicListResponse {
  given Encoder[ForumTopicListResponse] =
    Encoder.forProduct1("topics")(_.topics)

  def fromViews(views: Vector[ForumTopicView]): ForumTopicListResponse =
    ForumTopicListResponse(views.map(ForumTopicResponse.fromView))
}

final case class ForumTopicWrapperResponse(topic: ForumTopicResponse)

object ForumTopicWrapperResponse {
  given Encoder[ForumTopicWrapperResponse] =
    Encoder.forProduct1("topic")(_.topic)

  def fromView(view: ForumTopicView): ForumTopicWrapperResponse =
    ForumTopicWrapperResponse(ForumTopicResponse.fromView(view))
}
