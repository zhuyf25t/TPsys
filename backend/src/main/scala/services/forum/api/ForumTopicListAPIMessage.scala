package services.forum.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.forum.objects.apiTypes.{ForumApiRequestFields, ForumTopicListResponse}
import services.forum.services.ForumService
import system.api.APIMessageWithContext

final case class ForumTopicListAPIMessage(
  fields: ForumRequestFields
) extends APIMessageWithContext[ForumService, ForumTopicListResponse] {
  override def plan(service: ForumService, connection: Connection): IO[ForumTopicListResponse] =
    for
      topics <- service.listTopics(ForumAPIMessageSupport.viewerHandle(fields))
    yield ForumTopicListResponse.fromViews(topics)
}

object ForumTopicListAPIMessage {
  given Decoder[ForumTopicListAPIMessage] =
    Decoder[ForumApiRequestFields].map(fields => ForumTopicListAPIMessage(ForumRequestFields.fromApi(fields)))
}
