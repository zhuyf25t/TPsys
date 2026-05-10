package slaydemo.backend.forum.routes

import slaydemo.backend.forum.objects.{ForumReplyView, ForumTopicView, ForumVoteChoice}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object ForumRouteJsonRenderer {
  def renderTopics(topics: Vector[ForumTopicView]): String =
    renderObject(Vector("topics" -> topics.map(renderTopic).mkString("[", ",", "]")))

  def renderTopicWrapper(topic: ForumTopicView): String =
    renderObject(Vector("topic" -> renderTopic(topic)))

  private def renderTopic(topic: ForumTopicView): String =
    renderObject(
      Vector(
        "id" -> jsonString(topic.id.value),
        "title" -> jsonString(topic.title.value),
        "author" -> jsonString(topic.author.value),
        "excerpt" -> jsonString(topic.excerpt),
        "tag" -> jsonString(topic.tag.value),
        "replies" -> topic.replies.value.toString,
        "updatedAt" -> topic.updatedAt.value.toString,
        "createdAt" -> topic.createdAt.value.toString,
        "body" -> jsonString(topic.body.value),
        "replyItems" -> topic.replyItems.map(renderReply).mkString("[", ",", "]"),
        "viewerVote" -> renderVote(topic.viewerVote),
        "score" -> topic.score.value.toString
      )
    )

  private def renderReply(reply: ForumReplyView): String =
    renderObject(
      Vector(
        "id" -> jsonString(reply.id.value),
        "author" -> jsonString(reply.author.value),
        "body" -> jsonString(reply.body.value),
        "publishedAt" -> reply.publishedAt.value.toString,
        "viewerVote" -> renderVote(reply.viewerVote),
        "score" -> reply.score.value.toString
      )
    )

  private def renderVote(value: Option[ForumVoteChoice]): String =
    value.map(choice => jsonString(ForumVoteChoice.wireValue(choice))).getOrElse("null")

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
