package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.*
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object BattleQueueRoomJsonRenderer {
  def renderQueueSnapshot(snapshot: BattleQueueSnapshot): String =
    s"""{"ticketId":${jsonString(snapshot.ticketId.value)},"playerId":${jsonString(snapshot.playerId.value)},"roomId":${jsonString(snapshot.roomId.value)},"createdAt":${snapshot.createdAt.value},"startsAt":${snapshot.startsAt.value},"deadline":${snapshot.deadline.value},"serverTime":${snapshot.serverTime.value},"participants":${renderParticipants(snapshot.participants)},"capacity":${snapshot.capacity.value},"durationMs":${snapshot.durationMs.value},"phase":${jsonString(MatchmakingRoomPhase.wireValue(snapshot.phase))},"finishedAt":${renderOptionalMillis(snapshot.finishedAt)},"battleSession":${renderOptionalBattleSession(snapshot.battleSession)}}"""

  def renderRoomSnapshot(snapshot: RealtimeRoomSnapshot): String =
    s"""{"roomId":${jsonString(snapshot.roomId.value)},"serverTime":${snapshot.serverTime.value},"participants":${renderParticipants(snapshot.participants)},"capacity":${snapshot.capacity.value},"phase":${jsonString(MatchmakingRoomPhase.wireValue(snapshot.phase))},"finishedAt":${renderOptionalMillis(snapshot.finishedAt)},"battleSession":${renderOptionalBattleSession(snapshot.battleSession)}}"""

  private def renderParticipants(participants: Vector[BattleQueueParticipant]): String =
    participants.map(renderParticipant).mkString("[", ",", "]")

  private def renderParticipant(participant: BattleQueueParticipant): String =
    renderObject(
      Vector(
        "playerId" -> jsonString(participant.playerId.value),
        "handle" -> jsonString(participant.handle.value),
        "joinedAt" -> participant.joinedAt.value.toString,
        "lastSeen" -> participant.lastSeen.value.toString
      ) ++ optionalNumberField("rating", participant.rating.map(_.value)) ++
        optionalStringField("avatar", participant.avatar) ++
        optionalStringField("skin", participant.skin)
    )

  private def renderOptionalBattleSession(session: Option[BattleSessionDescriptor]): String =
    session.map(renderBattleSession).getOrElse("null")

  private def renderBattleSession(session: BattleSessionDescriptor): String =
    renderObject(
      Vector(
        "battleId" -> jsonString(session.battleId.value),
        "startedAt" -> session.startedAt.value.toString,
        "serverTime" -> session.serverTime.value.toString,
        "roster" -> session.roster.map(renderRosterEntry).mkString("[", ",", "]"),
        "capacity" -> session.capacity.value.toString,
        "bootstrap" -> session.bootstrap.map(renderBootstrap).getOrElse("null")
      )
    )

  private def renderRosterEntry(entry: BattleSessionRosterEntry): String =
    renderObject(
      Vector(
        "seat" -> entry.seat.value.toString,
        "playerId" -> jsonString(entry.playerId.value),
        "handle" -> jsonString(entry.handle.value),
        "joinedAt" -> entry.joinedAt.value.toString
      ) ++ optionalNumberField("rating", entry.rating.map(_.value)) ++
        optionalStringField("avatar", entry.avatar) ++
        optionalStringField("skin", entry.skin)
    )

  private def renderBootstrap(bootstrap: BattleSessionBootstrap): String =
    renderObject(Vector("seats" -> bootstrap.seats.map(renderBootstrapSeat).mkString("[", ",", "]")))

  private def renderBootstrapSeat(seat: BattleSessionBootstrapSeat): String =
    renderObject(
      Vector(
        "seat" -> seat.seat.value.toString,
        "playerId" -> jsonString(seat.playerId.value),
        "heroId" -> jsonString(seat.heroId.value),
        "handle" -> jsonString(seat.handle.value),
        "displayName" -> jsonString(seat.displayName.value),
        "joinedAt" -> seat.joinedAt.value.toString,
        "isBot" -> seat.isBot.toString,
        "spawnPointIndex" -> seat.spawnPointIndex.value.toString
      ) ++ optionalNumberField("rating", seat.rating.map(_.value)) ++
        optionalStringField("avatar", seat.avatar) ++
        optionalStringField("skin", seat.skin)
    )

  private def optionalNumberField(key: String, value: Option[Int]): Vector[(String, String)] =
    value.map(number => Vector(key -> number.toString)).getOrElse(Vector.empty)

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderOptionalMillis(value: Option[EpochMillis]): String =
    value.map(_.value.toString).getOrElse("null")

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
