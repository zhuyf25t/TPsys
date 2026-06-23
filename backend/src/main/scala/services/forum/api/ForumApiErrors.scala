package services.forum.api

import services.forum.services.{ForumCreateTopicError, ForumTopicMutationError}

enum ForumApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case InvalidTitle
  case InvalidBody
  case InvalidTag
  case InvalidAuthor
  case InvalidVote
  case VisitorNotAllowed
  case TopicNotFound
  case ReplyNotFound
}

object ForumApiErrorCode {
  def wireValue(code: ForumApiErrorCode): String =
    code match {
      case ForumApiErrorCode.MethodNotAllowed  => "method_not_allowed"
      case ForumApiErrorCode.InvalidJsonObject => "bad_request"
      case ForumApiErrorCode.InvalidTitle      => "invalid_title"
      case ForumApiErrorCode.InvalidBody       => "invalid_body"
      case ForumApiErrorCode.InvalidTag        => "invalid_tag"
      case ForumApiErrorCode.InvalidAuthor     => "invalid_author"
      case ForumApiErrorCode.InvalidVote       => "invalid_vote"
      case ForumApiErrorCode.VisitorNotAllowed => "visitor_not_allowed"
      case ForumApiErrorCode.TopicNotFound     => "topic_not_found"
      case ForumApiErrorCode.ReplyNotFound     => "reply_not_found"
    }

  def message(code: ForumApiErrorCode): String =
    code match {
      case ForumApiErrorCode.MethodNotAllowed  => "Method is not allowed."
      case ForumApiErrorCode.InvalidJsonObject => "Request body must be a JSON object with string fields."
      case _                                   => wireValue(code)
    }

  def statusCode(code: ForumApiErrorCode): Int =
    code match {
      case ForumApiErrorCode.MethodNotAllowed  => 405
      case ForumApiErrorCode.VisitorNotAllowed => 403
      case ForumApiErrorCode.TopicNotFound     => 404
      case ForumApiErrorCode.ReplyNotFound     => 404
      case _                                   => 400
    }
}

object ForumApiErrorMapper {
  def createErrorCode(error: ForumCreateTopicParseError): ForumApiErrorCode =
    error match {
      case ForumCreateTopicParseError.InvalidTitle      => ForumApiErrorCode.InvalidTitle
      case ForumCreateTopicParseError.InvalidBody       => ForumApiErrorCode.InvalidBody
      case ForumCreateTopicParseError.InvalidTag        => ForumApiErrorCode.InvalidTag
      case ForumCreateTopicParseError.InvalidAuthor     => ForumApiErrorCode.InvalidAuthor
      case ForumCreateTopicParseError.VisitorNotAllowed => ForumApiErrorCode.VisitorNotAllowed
    }

  def createErrorCode(error: ForumCreateTopicError): ForumApiErrorCode =
    error match {
      case ForumCreateTopicError.InvalidTitle      => ForumApiErrorCode.InvalidTitle
      case ForumCreateTopicError.InvalidBody       => ForumApiErrorCode.InvalidBody
      case ForumCreateTopicError.InvalidTag        => ForumApiErrorCode.InvalidTag
      case ForumCreateTopicError.InvalidAuthor     => ForumApiErrorCode.InvalidAuthor
      case ForumCreateTopicError.VisitorNotAllowed => ForumApiErrorCode.VisitorNotAllowed
    }

  def mutationErrorCode(error: ForumTopicMutationError): ForumApiErrorCode =
    error match {
      case ForumTopicMutationError.TopicNotFound => ForumApiErrorCode.TopicNotFound
      case ForumTopicMutationError.ReplyNotFound => ForumApiErrorCode.ReplyNotFound
    }

  def mutationErrorCode(error: ForumTopicMutationParseError): ForumApiErrorCode =
    error match {
      case ForumTopicMutationParseError.TopicNotFound     => ForumApiErrorCode.TopicNotFound
      case ForumTopicMutationParseError.ReplyNotFound     => ForumApiErrorCode.ReplyNotFound
      case ForumTopicMutationParseError.InvalidBody       => ForumApiErrorCode.InvalidBody
      case ForumTopicMutationParseError.InvalidAuthor     => ForumApiErrorCode.InvalidAuthor
      case ForumTopicMutationParseError.VisitorNotAllowed => ForumApiErrorCode.VisitorNotAllowed
    }
}

enum ForumApiRequestDecodeError {
  case InvalidJsonObject
}

enum ForumVoteCommandParseError {
  case InvalidVote
  case Mutation(error: ForumTopicMutationParseError)
}
