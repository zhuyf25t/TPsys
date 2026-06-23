package services.replay.api

import services.battle.microservices.actors.objects.player.BattleSurvivalOutcome
import services.battle.microservices.actors.objects.player.Score
import services.battle.objects.{BattleId, DurationMillis, EpochMillis}
import services.identity.objects.PlayerHandle
import services.identity.objects.DisplayName
import services.replay.objects.{ReplayFrameCount, ReplayPlaybackAvailability}
import system.policies.HandlePolicy

final case class ReplayListLimitInput(value: Int) extends AnyVal

object ReplayListLimitInput {
  val Default: ReplayListLimitInput =
    ReplayListLimitInput(25)

  def fromWire(value: Option[Int]): ReplayListLimitInput =
    ReplayListLimitInput(value.getOrElse(Default.value))
}

object ReplaySelectedHandleInput {
  def fromWire(value: Option[String]): Option[PlayerHandle] =
    value
      .map(HandlePolicy.trim)
      .filter(HandlePolicy.isPlayableIdentityHandle)
      .flatMap(PlayerHandle.forLookup)
}

object ReplayRecordBattleIdInput {
  def fromWire(value: Option[String]): Option[BattleId] =
    value
      .flatMap(nonEmpty)
      .filter(_.length <= 200)
      .map(BattleId.apply)
}

enum ReplayRecordHandleInput {
  case Valid(handle: PlayerHandle)
  case Invalid
  case VisitorNotAllowed
}

object ReplayRecordHandleInput {
  def fromWire(value: Option[String]): ReplayRecordHandleInput = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then ReplayRecordHandleInput.Invalid
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then ReplayRecordHandleInput.VisitorNotAllowed
    else PlayerHandle.forLookup(trimmed).map(ReplayRecordHandleInput.Valid.apply).getOrElse(ReplayRecordHandleInput.Invalid)
  }
}

object ReplayRecordDisplayNameInput {
  def fromWire(value: Option[String]): Option[DisplayName] =
    value.flatMap(nonEmpty).map(DisplayName.apply)
}

object ReplayRecordTimestampInput {
  def fromWire(value: Option[Long]): EpochMillis =
    EpochMillis(math.max(0L, value.getOrElse(0L)))
}

object ReplayRecordDurationInput {
  def fromWire(value: Option[Long]): DurationMillis =
    DurationMillis(math.max(0L, value.getOrElse(0L)))
}

object ReplayRecordScoreInput {
  def fromWire(value: Option[Int]): Score =
    Score(math.max(0, value.getOrElse(0)))
}

object ReplayRecordSurvivalInput {
  def fromWire(value: Option[Boolean]): BattleSurvivalOutcome =
    BattleSurvivalOutcome.fromAliveAtEnd(value.getOrElse(false))
}

object ReplayRecordFrameCountInput {
  def fromWire(value: Option[Int]): ReplayFrameCount =
    ReplayFrameCount.fromWire(value.getOrElse(0))
}

object ReplayRecordPlaybackInput {
  def fromWire(value: Option[Boolean]): ReplayPlaybackAvailability =
    ReplayPlaybackAvailability.fromAvailableFlag(value.getOrElse(false))
}

final case class ReplayRecordTextInput(value: String) extends AnyVal

object ReplayRecordTextInput {
  val Empty: ReplayRecordTextInput =
    ReplayRecordTextInput("")

  def fromWire(value: Option[String]): ReplayRecordTextInput =
    ReplayRecordTextInput(value.getOrElse(""))
}

final case class ReplayRecordOptionalTextInput(value: Option[String]) extends AnyVal

object ReplayRecordOptionalTextInput {
  val Empty: ReplayRecordOptionalTextInput =
    ReplayRecordOptionalTextInput(None)

  def fromWire(value: Option[String]): ReplayRecordOptionalTextInput =
    ReplayRecordOptionalTextInput(value.flatMap(nonEmpty).filter(_ != "null"))
}

final case class ReplayRecordFramesInput(value: String) extends AnyVal

object ReplayRecordFramesInput {
  val Empty: ReplayRecordFramesInput =
    ReplayRecordFramesInput("[]")

  def fromWire(framesJson: Option[String], frames: Option[ReplayRecordFramesPayloadInput]): ReplayRecordFramesInput =
    ReplayRecordFramesInput(framesJson.orElse(frames.map(_.value)).getOrElse(Empty.value))
}

final case class ReplayRecordFramesJsonInput(value: String) extends AnyVal

object ReplayRecordFramesJsonInput {
  def fromWire(value: String): ReplayRecordFramesJsonInput =
    ReplayRecordFramesJsonInput(value)
}

final case class ReplayRecordFramesPayloadInput(value: String) extends AnyVal

enum ReplayCommentAuthorInput {
  case Valid(handle: PlayerHandle)
  case Invalid
  case VisitorNotAllowed
}

object ReplayCommentAuthorInput {
  def fromWire(value: Option[String]): ReplayCommentAuthorInput = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then ReplayCommentAuthorInput.Invalid
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then ReplayCommentAuthorInput.VisitorNotAllowed
    else PlayerHandle.forLookup(trimmed).map(ReplayCommentAuthorInput.Valid.apply).getOrElse(ReplayCommentAuthorInput.Invalid)
  }
}

final case class ReplayCommentBodyInput(value: String) extends AnyVal

object ReplayCommentBodyInput {
  def fromWire(value: Option[String]): ReplayCommentBodyInput =
    ReplayCommentBodyInput(value.getOrElse(""))
}

private def nonEmpty(value: String): Option[String] =
  Option(value).map(_.trim).filter(_.nonEmpty)
