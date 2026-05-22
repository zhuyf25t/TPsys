package services.battle.services.queue

import services.battle.services.*

import services.battle.objects.*
import services.bots.objects.DemoBotProfiles
import services.identity.objects.{DisplayName, PlayerHandle}

private[services] final case class BattleRoomBootstrapParticipant(
  playerId: PlayerId,
  participant: BattleQueueParticipant
)

private[services] object BattleRoomBootstrapper {
  private val HeroSlotIds: Vector[HeroId] =
    Vector("player-1", "bot-1", "bot-2", "bot-3", "bot-4", "bot-5").map(HeroId.apply)

  /** 中文名：创建会话（createSession）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def createSession(
    battleId: BattleId,
    startsAt: EpochMillis,
    now: EpochMillis,
    capacity: BattleCapacity,
    participants: Vector[BattleRoomBootstrapParticipant]
  ): BattleSessionDescriptor = {
    val roster = participants.zipWithIndex.map { case (entry, index) =>
      val participant = entry.participant
      BattleSessionRosterEntry(
        seat = SeatIndex(index),
        playerId = entry.playerId,
        handle = participant.handle,
        joinedAt = participant.joinedAt,
        rating = participant.rating,
        avatar = participant.avatar,
        skin = participant.skin
      )
    }
    val humanSeats = participants.zipWithIndex.map { case (entry, index) =>
      val participant = entry.participant
      BattleSessionBootstrapSeat(
        seat = SeatIndex(index),
        playerId = entry.playerId,
        heroId = HeroId(s"hero-${entry.playerId.value}"),
        handle = participant.handle,
        displayName = DisplayName(participant.handle.value),
        joinedAt = participant.joinedAt,
        participantKind = BattleParticipantKind.Human,
        spawnPointIndex = SpawnPointIndex(index),
        rating = participant.rating,
        avatar = participant.avatar,
        skin = participant.skin
      )
    }
    val botSeats =
      (participants.length until capacity.value).toVector.map(buildBotSeat)

    BattleSessionDescriptor(
      battleId = battleId,
      startedAt = startsAt,
      serverTime = now,
      roster = roster,
      capacity = capacity,
      bootstrap = Some(BattleSessionBootstrap(humanSeats ++ botSeats))
    )
  }

  private def buildBotSeat(index: Int): BattleSessionBootstrapSeat = {
    val profile = Option.when(index > 0)(index - 1).flatMap(DemoBotProfiles.all.lift)
    val handle = profile.map(_.handle).getOrElse(PlayerHandle(s"Bot $index"))
    val avatar = profile.map(_.skin.avatarKey.value)

    BattleSessionBootstrapSeat(
      seat = SeatIndex(index),
      playerId = PlayerId(s"bot-seat-$index"),
      heroId = HeroSlotIds.lift(index).getOrElse(HeroId(s"bot-$index")),
      handle = handle,
      displayName = profile.map(_.displayName).getOrElse(DisplayName(handle.value)),
      joinedAt = EpochMillis(0L),
      participantKind = BattleParticipantKind.Bot,
      spawnPointIndex = SpawnPointIndex(index),
      rating = profile.map(profile => Rating(profile.initialRating.value)),
      avatar = avatar,
      skin = avatar
    )
  }
}
