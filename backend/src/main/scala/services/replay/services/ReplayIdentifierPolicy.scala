package services.replay.services

import services.replay.objects.ReplayId

object ReplayIdentifierPolicy {
  private val MaxReplayIdLength: Int = 200

  def isSafeReplayId(replayId: ReplayId): Boolean =
    isSafeIdentifier(replayId.value)

  def isSafeIdentifier(value: String): Boolean = {
    val trimmed = Option(value).getOrElse("").trim
    trimmed.nonEmpty &&
      trimmed.length <= MaxReplayIdLength &&
      trimmed.forall(char => char.isLetterOrDigit || char == '-' || char == '_' || char == '.' || char == '~')
  }
}
