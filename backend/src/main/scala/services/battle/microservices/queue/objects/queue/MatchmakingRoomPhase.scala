package services.battle.microservices.queue.objects.queue

enum MatchmakingRoomPhase {
  case Waiting
  case Active
  case Finished
  case Unknown
}

object MatchmakingRoomPhase {
  def wireValue(value: MatchmakingRoomPhase): String =
    value match {
      case MatchmakingRoomPhase.Waiting  => "waiting"
      case MatchmakingRoomPhase.Active   => "active"
      case MatchmakingRoomPhase.Finished => "finished"
      case MatchmakingRoomPhase.Unknown  => "unknown"
    }

  def fromWire(value: String): Option[MatchmakingRoomPhase] =
    value match {
      case "waiting"  => Some(MatchmakingRoomPhase.Waiting)
      case "active"   => Some(MatchmakingRoomPhase.Active)
      case "finished" => Some(MatchmakingRoomPhase.Finished)
      case "unknown"  => Some(MatchmakingRoomPhase.Unknown)
      case _          => None
    }
}
