package services.battle.microservices.queue.services

import cats.effect.IO
import cats.syntax.all.*

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
  private val ZombieHeroSlotIds: Vector[HeroId] =
    (Vector("player-1") ++ (1 to 11).toVector.map(index => s"bot-$index")).map(HeroId.apply)
  private val ZombieFallbackSkin: BattleSkinKey =
    BattleSkinKey.fromWire("zombie").getOrElse(throw IllegalStateException("invalid zombie skin key"))
  private val ZombieFallbackAvatar: BattleAvatarKey =
    BattleAvatarKey.fromWire("zombie").getOrElse(throw IllegalStateException("invalid zombie avatar key"))

  private final case class CombatBotProfile(
    handle: PlayerHandle,
    displayName: DisplayName,
    avatar: BattleAvatarKey,
    skin: BattleSkinKey
  )

  private val CombatBotProfiles: Vector[CombatBotProfile] =
    Vector(
      combatBotProfile("cpu-sable", "Sable", "soldier"),
      combatBotProfile("cpu-rivet", "Rivet", "brown"),
      combatBotProfile("cpu-ember", "Ember", "old"),
      combatBotProfile("cpu-orbit", "Orbit", "woman"),
      combatBotProfile("cpu-nova", "Nova", "survivor"),
      combatBotProfile("cpu-byte", "Byte", "blue"),
      combatBotProfile("cpu-vex", "Vex", "soldier"),
      combatBotProfile("cpu-kite", "Kite", "brown"),
      combatBotProfile("cpu-lynx", "Lynx", "woman"),
      combatBotProfile("cpu-echo", "Echo", "old"),
      combatBotProfile("cpu-ash", "Ash", "survivor")
    )

  /** 中文名：创建会话（createSession）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def createSession(
    battleId: BattleId,
    battleMode: BattleMode,
    startsAt: EpochMillis,
    now: EpochMillis,
    capacity: BattleCapacity,
    participants: Vector[BattleRoomBootstrapParticipant]
  ): IO[BattleSessionDescriptor] =
    for
      roster <- participants.zipWithIndex.traverse { case (entry, index) => rosterEntry(entry, index) }
      humanSeats <- participants.zipWithIndex.traverse { case (entry, index) => humanSeat(entry, index) }
      botSeats <- (participants.length until capacity.value).toVector.traverse(index => buildBotSeat(battleMode, index))
      descriptor <- IO.pure(
        BattleSessionDescriptor(
          battleId = battleId,
          battleMode = battleMode,
          startedAt = startsAt,
          serverTime = now,
          roster = roster,
          capacity = capacity,
          bootstrap = Some(BattleSessionBootstrap(humanSeats ++ botSeats))
        )
      )
    yield descriptor

  private def rosterEntry(
    entry: BattleRoomBootstrapParticipant,
    index: Int
  ): IO[BattleSessionRosterEntry] = {
    val participant = entry.participant
    IO.pure(
      BattleSessionRosterEntry(
        seat = SeatIndex(index),
        playerId = entry.playerId,
        handle = participant.handle,
        joinedAt = participant.joinedAt,
        rating = participant.rating,
        avatar = participant.avatar,
        skin = participant.skin
      )
    )
  }

  private def humanSeat(
    entry: BattleRoomBootstrapParticipant,
    index: Int
  ): IO[BattleSessionBootstrapSeat] = {
    val participant = entry.participant
    IO.pure(
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
    )
  }

  private def buildBotSeat(battleMode: BattleMode, index: Int): IO[BattleSessionBootstrapSeat] =
    battleMode match {
      case BattleMode.Winter => buildZombieBotSeat(index)
      case _                 => buildCombatBotSeat(index)
    }

  private def buildZombieBotSeat(index: Int): IO[BattleSessionBootstrapSeat] = {
    val profile = Option.when(index > 0)(index - 1).flatMap(DemoBotProfiles.all.lift)
    IO.pure(BattleSessionBootstrapSeat(
      seat = SeatIndex(index),
      playerId = PlayerId(s"bot-seat-$index"),
      heroId = ZombieHeroSlotIds.lift(index).getOrElse(HeroId(s"bot-$index")),
      handle = profile.map(_.handle).getOrElse(PlayerHandle(s"cpu-zombie-$index")),
      displayName = profile.map(_.displayName).getOrElse(DisplayName(s"Zombie $index")),
      joinedAt = EpochMillis(0L),
      participantKind = BattleParticipantKind.Bot,
      spawnPointIndex = SpawnPointIndex(index),
      rating = profile.map(profile => Rating(profile.initialRating.value)).orElse(Some(Rating(1000))),
      avatar = Some(profile.flatMap(item => BattleAvatarKey.fromWire(item.skin.avatarKey.value)).getOrElse(ZombieFallbackAvatar)),
      skin = Some(profile.flatMap(item => BattleSkinKey.fromWire(item.skin.avatarKey.value)).getOrElse(ZombieFallbackSkin))
    ))
  }

  private def buildCombatBotSeat(index: Int): IO[BattleSessionBootstrapSeat] = {
    val profile = CombatBotProfiles.lift(math.max(0, index - 1)).getOrElse(combatBotProfile(s"cpu-bot-$index", s"Bot $index", "soldier"))
    IO.pure(BattleSessionBootstrapSeat(
      seat = SeatIndex(index),
      playerId = PlayerId(s"bot-seat-$index"),
      heroId = HeroId(s"combat-bot-$index"),
      handle = profile.handle,
      displayName = profile.displayName,
      joinedAt = EpochMillis(0L),
      participantKind = BattleParticipantKind.Bot,
      spawnPointIndex = SpawnPointIndex(index),
      rating = Some(Rating(1000 + index * 7)),
      avatar = Some(profile.avatar),
      skin = Some(profile.skin)
    ))
  }

  private def combatBotProfile(handle: String, displayName: String, skin: String): CombatBotProfile =
    CombatBotProfile(
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      avatar = BattleAvatarKey.fromWire(skin).getOrElse(throw IllegalStateException(s"invalid bot avatar key: $skin")),
      skin = BattleSkinKey.fromWire(skin).getOrElse(throw IllegalStateException(s"invalid bot skin key: $skin"))
    )
}
