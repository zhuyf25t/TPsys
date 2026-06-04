package services.battle.microservices.session.services

import cats.effect.IO

private[battle] object BattleFailureMessageFormatter {
  /** 中文名：throwablemessage（throwableMessage）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态。 */
  def throwableMessage(error: Throwable): IO[String] = IO.pure {
    val detail = Option(error.getMessage).map(_.trim).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    s"${error.getClass.getSimpleName}: $detail"
  }
}
