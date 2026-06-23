package services.forum.api

import cats.effect.IO

import services.forum.services.ForumService

object ForumAPIPlanner {
  def planCreateTopic(service: ForumService, message: ForumCreateTopicAPIMessage): IO[ForumTopicWrapperResponse] =
    for
      command <- IO.fromEither(
        ForumCommandParsers
          .parseCreateTopicCommand(message.title, message.body, message.tag, message.selectedAuthor)
          .left
          .map(ForumAPIMessageErrors.createParse)
      )
      topic <- service.createTopic(command).flatMap(ForumAPIMessageErrors.createService)
    yield ForumTopicWrapperResponse.fromView(topic)

  def planAddReply(service: ForumService, message: ForumAddReplyAPIMessage): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(message.topicId))
      command <- IO.fromEither(
        ForumCommandParsers
          .parseAddReplyCommand(topicId, message.body, message.selectedAuthor)
          .left
          .map(ForumAPIMessageErrors.mutationParse)
      )
      topic <- service.addReply(command).flatMap(ForumAPIMessageErrors.mutationService)
    yield ForumTopicWrapperResponse.fromView(topic)

  def planTopicLoad(service: ForumService, message: ForumTopicLoadAPIMessage): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(message.topicId))
      topic <- service.loadTopic(topicId, message.selectedViewer).flatMap(ForumAPIMessageErrors.topicLoad)
    yield ForumTopicWrapperResponse.fromView(topic)

  def planTopicList(service: ForumService, message: ForumTopicListAPIMessage): IO[ForumTopicListResponse] =
    for
      topics <- service.listTopics(message.selectedViewer)
    yield ForumTopicListResponse.fromViews(topics)

  def planSetTopicVote(service: ForumService, message: ForumSetTopicVoteAPIMessage): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(message.topicId))
      parsedVote <- IO.fromEither(ForumCommandParsers.parseVote(message.selectedVote).left.map(ForumAPIMessageErrors.voteParse))
      command <- IO.fromEither(
        ForumCommandParsers
          .parseSetTopicVoteCommand(topicId, message.selectedAuthor, parsedVote)
          .left
          .map(ForumAPIMessageErrors.voteMutationParse)
      )
      topic <- service.setTopicVote(command).flatMap(ForumAPIMessageErrors.mutationService)
    yield ForumTopicWrapperResponse.fromView(topic)

  def planSetReplyVote(service: ForumService, message: ForumSetReplyVoteAPIMessage): IO[ForumTopicWrapperResponse] =
    for
      topicId <- IO.fromEither(ForumAPIMessageSupport.topicId(message.topicId))
      replyId <- IO.fromEither(ForumAPIMessageSupport.replyId(message.replyId))
      parsedVote <- IO.fromEither(ForumCommandParsers.parseVote(message.selectedVote).left.map(ForumAPIMessageErrors.voteParse))
      command <- IO.fromEither(
        ForumCommandParsers
          .parseSetReplyVoteCommand(topicId, replyId, message.selectedAuthor, parsedVote)
          .left
          .map(ForumAPIMessageErrors.voteMutationParse)
      )
      topic <- service.setReplyVote(command).flatMap(ForumAPIMessageErrors.mutationService)
    yield ForumTopicWrapperResponse.fromView(topic)
}
