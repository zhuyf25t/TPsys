package slaydemo.backend.forum.database

import java.nio.charset.StandardCharsets
import java.util.Base64

import slaydemo.backend.forum.objects.{ForumReplyRecord, ForumTopicId, ForumTopicRecord, ForumVoteChoice}

private[database] object ForumFileJsonRenderer {
  def renderPayload(topics: Vector[ForumTopicRecord]): String = {
    val renderedTopics = topics.map(renderTopic).mkString(",\n")
    val renderedReplies = topics.flatMap(topic => topic.replies.map(renderReply(topic.id, _))).mkString(",\n")
    val renderedVotes = topics.flatMap(renderTopicVotes).mkString(",\n")
    val renderedReplyVotes = topics.flatMap(topic => topic.replies.flatMap(renderReplyVotes)).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.forum.v2",
       |  "topics": [
       |$renderedTopics
       |  ],
       |  "replies": [
       |$renderedReplies
       |  ],
       |  "votes": [
       |$renderedVotes
       |  ],
       |  "replyVotes": [
       |$renderedReplyVotes
       |  ]
       |}
       |""".stripMargin
  }

  private def renderTopic(topic: ForumTopicRecord): String =
    s"""    {
       |      "topicId": "${encode(topic.id.value)}",
       |      "title": "${encode(topic.title.value)}",
       |      "body": "${encode(topic.body.value)}",
       |      "tag": "${encode(topic.tag.value)}",
       |      "authorHandle": "${encode(topic.authorHandle.value)}",
       |      "createdAt": ${topic.createdAt.value},
       |      "updatedAt": ${topic.updatedAt.value}
       |    }""".stripMargin

  private def renderReply(topicId: ForumTopicId, reply: ForumReplyRecord): String =
    s"""    {
       |      "replyId": "${encode(reply.id.value)}",
       |      "topicId": "${encode(topicId.value)}",
       |      "authorHandle": "${encode(reply.authorHandle.value)}",
       |      "body": "${encode(reply.body.value)}",
       |      "createdAt": ${reply.createdAt.value}
       |    }""".stripMargin

  private def renderTopicVotes(topic: ForumTopicRecord): Vector[String] =
    topic.votes.valuesByVoter.toVector.sortBy(_._1.value).map { case (voter, choice) =>
      s"""    {
         |      "topicId": "${encode(topic.id.value)}",
         |      "authorHandle": "${encode(voter.value)}",
         |      "vote": "${ForumVoteChoice.wireValue(choice)}"
         |    }""".stripMargin
    }

  private def renderReplyVotes(reply: ForumReplyRecord): Vector[String] =
    reply.votes.valuesByVoter.toVector.sortBy(_._1.value).map { case (voter, choice) =>
      s"""    {
         |      "replyId": "${encode(reply.id.value)}",
         |      "authorHandle": "${encode(voter.value)}",
         |      "vote": "${ForumVoteChoice.wireValue(choice)}"
         |    }""".stripMargin
    }

  private def encode(value: String): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))
}
