package slaydemo.backend.forum.routes

import slaydemo.backend.forum.services.{ForumCreateTopicError, ForumTopicMutationError}

object ForumRouteErrorMapper {
  def createErrorCode(error: ForumCreateTopicParseError): String =
    error match {
      case ForumCreateTopicParseError.InvalidTitle      => "invalid_title"
      case ForumCreateTopicParseError.InvalidBody       => "invalid_body"
      case ForumCreateTopicParseError.InvalidTag        => "invalid_tag"
      case ForumCreateTopicParseError.InvalidAuthor     => "invalid_author"
      case ForumCreateTopicParseError.VisitorNotAllowed => "visitor_not_allowed"
    }

  def createStatusFor(error: ForumCreateTopicParseError): Int =
    error match {
      case ForumCreateTopicParseError.VisitorNotAllowed => 403
      case _                                           => 400
    }

  def createErrorCode(error: ForumCreateTopicError): String =
    error match {
      case ForumCreateTopicError.InvalidTitle      => "invalid_title"
      case ForumCreateTopicError.InvalidBody       => "invalid_body"
      case ForumCreateTopicError.InvalidTag        => "invalid_tag"
      case ForumCreateTopicError.InvalidAuthor     => "invalid_author"
      case ForumCreateTopicError.VisitorNotAllowed => "visitor_not_allowed"
    }

  def createStatusFor(error: ForumCreateTopicError): Int =
    error match {
      case ForumCreateTopicError.VisitorNotAllowed => 403
      case _                                      => 400
    }

  def mutationErrorCode(error: ForumTopicMutationError): String =
    error match {
      case ForumTopicMutationError.TopicNotFound => "topic_not_found"
      case ForumTopicMutationError.ReplyNotFound => "reply_not_found"
    }

  def mutationStatusFor(error: ForumTopicMutationError): Int =
    error match {
      case ForumTopicMutationError.TopicNotFound => 404
      case ForumTopicMutationError.ReplyNotFound => 404
    }

  def mutationErrorCode(error: ForumTopicMutationParseError): String =
    error match {
      case ForumTopicMutationParseError.TopicNotFound     => "topic_not_found"
      case ForumTopicMutationParseError.ReplyNotFound     => "reply_not_found"
      case ForumTopicMutationParseError.InvalidBody       => "invalid_body"
      case ForumTopicMutationParseError.InvalidAuthor     => "invalid_author"
      case ForumTopicMutationParseError.VisitorNotAllowed => "visitor_not_allowed"
    }

  def mutationStatusFor(error: ForumTopicMutationParseError): Int =
    error match {
      case ForumTopicMutationParseError.TopicNotFound     => 404
      case ForumTopicMutationParseError.ReplyNotFound     => 404
      case ForumTopicMutationParseError.VisitorNotAllowed => 403
      case _                                             => 400
    }
}
