package services.battle.microservices.queue.services

import services.battle.microservices.queue.objects.queue.*

import services.bots.objects.DemoBotProfiles
import services.battle.objects.BattleMode
import services.battle.objects.core.{
  BattleId,
  EpochMillis,
  HeroId,
  PlayerId,
  SeatIndex,
  SpawnPointIndex
}
import services.battle.microservices.actors.objects.player.{BattleAvatarKey, BattleParticipantKind, BattleSkinKey, Rating}
import services.battle.microservices.queue.objects.queue.{
  BattleQueueParticipant,
  BattleSessionBootstrap,
  BattleSessionBootstrapSeat,
  BattleSessionDescriptor,
  BattleSessionRosterEntry
}
import services.identity.objects.{DisplayName, PlayerHandle}

private[battle] final case class BattleRoomBootstrapParticipant(
  playerId: PlayerId,
  participant: BattleQueueParticipant
)

private[battle] object BattleRoomBootstrapper {
  private val HeroSlotIds: Vector[HeroId] =
    Vector("player-1", "bot-1", "bot-2", "bot-3", "bot-4", "bot-5").map(HeroId.apply)

  /** 中文名：创建会话（createSession）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def createSession(
    battleId: BattleId,
    battleMode: BattleMode,
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
      battleMode = battleMode,
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
    val avatar = profile.flatMap(item => BattleAvatarKey.fromWire(item.skin.avatarKey.value))
    val skin = profile.flatMap(item => BattleSkinKey.fromWire(item.skin.avatarKey.value))

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
      skin = skin
    )
  }
}
