package services.bots.api

enum BotProfileApiErrorCode {
  case MethodNotAllowed
}

object BotProfileApiErrorCode {
  def wireValue(code: BotProfileApiErrorCode): String =
    code match {
      case BotProfileApiErrorCode.MethodNotAllowed => "method_not_allowed"
    }

  def message(code: BotProfileApiErrorCode): String =
    code match {
      case BotProfileApiErrorCode.MethodNotAllowed => "Method is not allowed."
    }

  def statusCode(code: BotProfileApiErrorCode): Int =
    code match {
      case BotProfileApiErrorCode.MethodNotAllowed => 405
    }
}
