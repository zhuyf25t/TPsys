package slaydemo.backend.replay.objects

enum ReplayPlaybackAvailability {
  case Available
  case Unavailable
}

object ReplayPlaybackAvailability {
  def fromAvailableFlag(available: Boolean): ReplayPlaybackAvailability =
    if available then ReplayPlaybackAvailability.Available else ReplayPlaybackAvailability.Unavailable

  def resolve(
    requested: ReplayPlaybackAvailability,
    frames: ReplayPlaybackAvailability
  ): ReplayPlaybackAvailability =
    (requested, frames) match {
      case (ReplayPlaybackAvailability.Available, ReplayPlaybackAvailability.Available) =>
        ReplayPlaybackAvailability.Available
      case _ =>
        ReplayPlaybackAvailability.Unavailable
    }

  def availableFlag(value: ReplayPlaybackAvailability): Boolean =
    value == ReplayPlaybackAvailability.Available
}
