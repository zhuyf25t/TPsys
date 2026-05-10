package slaydemo.backend.battle.services

private[services] object BattleFailureMessageFormatter {
  def throwableMessage(error: Throwable): String = {
    val detail = Option(error.getMessage).map(_.trim).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    s"${error.getClass.getSimpleName}: $detail"
  }
}
