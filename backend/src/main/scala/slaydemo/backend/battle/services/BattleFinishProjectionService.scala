package slaydemo.backend.battle.services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.util.control.NonFatal

import slaydemo.backend.battle.database.BattleResultRepository
import slaydemo.backend.battle.objects.*
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.mail.database.MailRepository
import slaydemo.backend.mail.objects.{MailId, MailKind, MailRecord}
import slaydemo.backend.replay.database.ReplayRepository
import slaydemo.backend.replay.objects.{ReplayId, ReplayRecord, ReplaySettlementRecord}
import slaydemo.backend.shared.policies.HandlePolicy

enum BattleFinishProjectionOutcome {
  case Projected
  case NotConfigured
  case ResultProjectedReplayFailed(message: String)
  case ResultFailedReplayProjected(message: String)
  case Failed(message: String)
}

object BattleFinishProjectionOutcome {
  def artifactStatus(value: BattleFinishProjectionOutcome): BattleArtifactStatus =
    value match {
      case BattleFinishProjectionOutcome.Projected                           => BattleArtifactStatus.Ready
      case BattleFinishProjectionOutcome.NotConfigured                       => BattleArtifactStatus.Pending
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(_)      => BattleArtifactStatus.ResultOnlyReady
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(_)      => BattleArtifactStatus.ReplayOnlyReady
      case BattleFinishProjectionOutcome.Failed(_)                           => BattleArtifactStatus.Pending
    }

  def failureMessage(value: BattleFinishProjectionOutcome): Option[String] =
    value match {
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) => Some(message)
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) => Some(message)
      case BattleFinishProjectionOutcome.Failed(message)                      => Some(message)
      case BattleFinishProjectionOutcome.Projected | BattleFinishProjectionOutcome.NotConfigured =>
        None
    }
}

trait BattleFinishProjector {
  def project(state: BattleAggregateState): BattleFinishProjectionOutcome
}

trait BattleFinishProjectionFailureReporter {
  def reportFailure(battleId: BattleId, message: String): Unit
}

object ConsoleBattleFinishProjectionFailureReporter extends BattleFinishProjectionFailureReporter {
  override def reportFailure(battleId: BattleId, message: String): Unit =
    Console.err.println(s"[battle-finish-projection] battleId=${battleId.value} failed: $message")
}

object NoopBattleFinishProjector extends BattleFinishProjector {
  override def project(state: BattleAggregateState): BattleFinishProjectionOutcome =
    BattleFinishProjectionOutcome.NotConfigured
}

final class DefaultBattleFinishProjector(
  battleResultRepository: BattleResultRepository,
  replayRepository: ReplayRepository,
  mailRepository: MailRepository,
  failureReporter: BattleFinishProjectionFailureReporter = ConsoleBattleFinishProjectionFailureReporter
) extends BattleFinishProjector {
  override def project(state: BattleAggregateState): BattleFinishProjectionOutcome =
    if state.phase != BattlePhase.Finished then BattleFinishProjectionOutcome.NotConfigured
    else
      try {
        val previousRatings = previousRatingsFor(
          state.battleId,
          BattleFinishProjectionPlanner.humanPlayersByPlacement(state)
        )
        val plan = BattleFinishProjectionPlanner.build(state, previousRatings)
        val resultOutcome =
          if BattleArtifactStatus.isResultReady(state.artifactStatus) then BattleProjectionArtifactWriteOutcome.Projected
          else writeResultArtifacts(state.battleId, plan)
        val replayOutcome =
          if BattleArtifactStatus.isReplayReady(state.artifactStatus) then BattleProjectionArtifactWriteOutcome.Projected
          else writeReplayArtifact(state.battleId, plan)
        combineArtifactOutcomes(resultOutcome, replayOutcome)
      } catch {
        case NonFatal(error) =>
          val message = failureMessage(error)
          failureReporter.reportFailure(state.battleId, message)
          BattleFinishProjectionOutcome.Failed(message)
      }

  private def writeResultArtifacts(
    battleId: BattleId,
    plan: BattleFinishProjectionPlan
  ): BattleProjectionArtifactWriteOutcome =
    catchArtifactWriteFailure("result", battleId) {
      plan.settlements.foreach { settlement =>
        val saved = battleResultRepository.save(settlement.result)
        mailRepository.save(battleMail(saved))
        if saved.ratingDelta != 0 then mailRepository.save(ratingMail(saved))
      }
    }

  private def writeReplayArtifact(
    battleId: BattleId,
    plan: BattleFinishProjectionPlan
  ): BattleProjectionArtifactWriteOutcome =
    catchArtifactWriteFailure("replay", battleId) {
      plan.replay.foreach(replayRepository.saveReplay)
    }

  private def catchArtifactWriteFailure(
    label: String,
    battleId: BattleId
  )(write: => Unit): BattleProjectionArtifactWriteOutcome =
    try {
      write
      BattleProjectionArtifactWriteOutcome.Projected
    } catch {
      case NonFatal(error) =>
        val message = failureMessage(error)
        failureReporter.reportFailure(battleId, s"$label: $message")
        BattleProjectionArtifactWriteOutcome.Failed(message)
    }

  private def combineArtifactOutcomes(
    resultOutcome: BattleProjectionArtifactWriteOutcome,
    replayOutcome: BattleProjectionArtifactWriteOutcome
  ): BattleFinishProjectionOutcome =
    (resultOutcome, replayOutcome) match {
      case (BattleProjectionArtifactWriteOutcome.Projected, BattleProjectionArtifactWriteOutcome.Projected) =>
        BattleFinishProjectionOutcome.Projected
      case (BattleProjectionArtifactWriteOutcome.Projected, BattleProjectionArtifactWriteOutcome.Failed(message)) =>
        BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message)
      case (BattleProjectionArtifactWriteOutcome.Failed(message), BattleProjectionArtifactWriteOutcome.Projected) =>
        BattleFinishProjectionOutcome.ResultFailedReplayProjected(message)
      case (BattleProjectionArtifactWriteOutcome.Failed(resultMessage), BattleProjectionArtifactWriteOutcome.Failed(replayMessage)) =>
        BattleFinishProjectionOutcome.Failed(s"result: $resultMessage; replay: $replayMessage")
    }

  private def battleMail(result: BattleResultRecord): MailRecord =
    MailRecord(
      id = MailId(s"mail-battle-${result.resultId.value}"),
      ownerHandle = result.handle,
      kind = MailKind.Battle,
      subject = "Battle settlement ready",
      excerpt = s"${result.resultLabel}: score ${result.score.value}, placement #${result.placement.getOrElse(0)}.",
      senderLabel = "Battle archive",
      unread = true,
      important = true,
      createdAt = result.finishedAt,
      sourceBattleId = Some(result.battleId.value),
      sourcePath = Some(replaySourcePath(result)),
      sourceLabel = Some("View replay")
    )

  private def ratingMail(result: BattleResultRecord): MailRecord =
    MailRecord(
      id = MailId(s"mail-rating-${result.resultId.value}"),
      ownerHandle = result.handle,
      kind = MailKind.Reward,
      subject = "Rating updated",
      excerpt = s"Rating ${signed(result.ratingDelta)} to ${result.ratingAfter.value}.",
      senderLabel = "Rating service",
      unread = true,
      important = false,
      createdAt = result.finishedAt,
      sourceBattleId = Some(result.battleId.value),
      sourcePath = Some(replaySourcePath(result)),
      sourceLabel = Some("View replay")
    )

  private def replaySourcePath(result: BattleResultRecord): String =
    s"/replay/${urlEncode(result.battleId.value)}?handle=${urlEncode(result.handle.value)}"

  private def previousRatingsFor(
    battleId: BattleId,
    players: Vector[BattlePlayerState]
  ): BattlePreviousRatings =
    BattlePreviousRatings.fromRatings(
      players.map(player => player.handle -> fetchPreviousRating(battleId, player.handle))
    )

  private def fetchPreviousRating(battleId: BattleId, handle: PlayerHandle): Rating =
    battleResultRepository
      .list(Some(handle), None, 25)
      .filterNot(_.battleId == battleId)
      .headOption
      .map(_.ratingAfter)
      .getOrElse(BattleFinishProjectionPlanner.DefaultRating)

  private def urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  private def signed(value: Int): String =
    if value > 0 then s"+$value" else value.toString

  private def failureMessage(error: Throwable): String = {
    val detail = Option(error.getMessage).map(_.trim).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    s"${error.getClass.getSimpleName}: $detail"
  }
}

object DefaultBattleFinishProjector {
  def apply(
    battleResultRepository: BattleResultRepository,
    replayRepository: ReplayRepository,
    mailRepository: MailRepository
  ): DefaultBattleFinishProjector =
    new DefaultBattleFinishProjector(battleResultRepository, replayRepository, mailRepository)
}

private[services] enum BattleProjectionArtifactWriteOutcome {
  case Projected
  case Failed(message: String)
}

private[services] final case class BattleSettlement(
  player: Option[BattlePlayerState],
  result: BattleResultRecord
)

private[services] final case class BattleFinishProjectionPlan(
  settlements: Vector[BattleSettlement],
  replay: Option[ReplayRecord]
)

private[services] final case class BattlePreviousRatings private (
  ratingsByHandleKey: Map[String, Rating]
) {
  def ratingBefore(handle: PlayerHandle): Rating =
    ratingsByHandleKey.getOrElse(handle.key, BattleFinishProjectionPlanner.DefaultRating)
}

private[services] object BattlePreviousRatings {
  val empty: BattlePreviousRatings =
    BattlePreviousRatings(Map.empty)

  def fromRatings(ratings: Iterable[(PlayerHandle, Rating)]): BattlePreviousRatings =
    BattlePreviousRatings(
      ratings.map { case (handle, rating) => handle.key -> rating }.toMap
    )
}

private[services] object BattleFinishProjectionPlanner {
  private val TimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

  def build(
    state: BattleAggregateState,
    previousRatings: BattlePreviousRatings
  ): BattleFinishProjectionPlan = {
    val settlements = buildSettlements(state, previousRatings)
    BattleFinishProjectionPlan(
      settlements = settlements,
      replay = Some(replayRecord(state, settlements))
    )
  }

  def humanPlayersByPlacement(state: BattleAggregateState): Vector[BattlePlayerState] =
    playersByPlacement(state.players).filter(isPlayableHumanPlayer)

  private def buildSettlements(
    state: BattleAggregateState,
    previousRatings: BattlePreviousRatings
  ): Vector[BattleSettlement] = {
    val orderedPlayers = playersByPlacement(state.players)
    val placementsByPlayerId = orderedPlayers.zipWithIndex.map { case (player, index) =>
      player.playerId -> (index + 1)
    }.toMap
    val playersLine = playersLineFor(state.players)
    val finishedAt = projectedFinishedAt(state)
    val durationMs = projectedDuration(state)
    val playerCount = orderedPlayers.length

    val playableHumanSettlements = orderedPlayers
      .filter(isPlayableHumanPlayer)
      .map { player =>
        val placement = placementsByPlayerId.getOrElse(player.playerId, orderedPlayers.length)
        val score = placementScore(Some(placement), playerCount)
        val ratingBefore = previousRatings.ratingBefore(player.handle)
        val ratingDelta = calculateRatingDelta(score, Some(placement), player.alive)
        val ratingAfter = Rating(ratingBefore.value + ratingDelta)
        val result = BattleResultRecord(
          battleId = state.battleId,
          handle = player.handle,
          displayName = player.displayName,
          finishedAt = finishedAt,
          finishedAtLabel = formatFinishedAt(finishedAt),
          durationMs = durationMs,
          score = Score(score),
          placement = Some(placement),
          aliveAtEnd = player.alive,
          ratingBefore = ratingBefore,
          ratingDelta = ratingDelta,
          ratingAfter = ratingAfter,
          resultLabel = resultLabel(player, placement),
          modeLabel = "权威对战",
          mapLabel = "权威竞技场",
          highlightLine = highlightLine(player, placement, score),
          playersLine = playersLine,
          timelineHint = timelineHint(player),
          currentLoadout = None
        )

        BattleSettlement(player = Some(player), result = result)
      }

    if playableHumanSettlements.nonEmpty then playableHumanSettlements
    else Vector(BattleSettlement(player = None, result = serverResult(state, finishedAt, durationMs, playersLine, previousRatings)))
  }

  private def replayRecord(state: BattleAggregateState, settlements: Vector[BattleSettlement]): ReplayRecord = {
    val result = replayOwnerSettlement(state, settlements).result
    val replayFrames = replayFramesJson(state)
    ReplayRecord(
      replayId = ReplayId(state.battleId.value),
      battleId = state.battleId,
      handle = result.handle,
      displayName = result.displayName,
      finishedAt = result.finishedAt,
      finishedAtLabel = result.finishedAtLabel,
      title = replayTitle(result),
      modeLabel = result.modeLabel,
      resultLabel = replayResultLabel(state),
      mapLabel = result.mapLabel,
      highlightLine = result.highlightLine,
      coverLabel = "服务器战报",
      playersLine = result.playersLine,
      timelineHint = result.timelineHint,
      score = result.score,
      placement = result.placement,
      ratingBefore = Some(result.ratingBefore),
      ratingDelta = Some(result.ratingDelta),
      ratingAfter = Some(result.ratingAfter),
      durationMs = result.durationMs,
      aliveAtEnd = result.aliveAtEnd,
      thumbnailDataUrl = None,
      currentLoadout = result.currentLoadout,
      frameCount = replayFrames.frameCount,
      playbackAvailable = replayFrames.frameCount >= 2,
      framesJson = replayFrames.json,
      settlements = settlements.map(settlement => replaySettlement(settlement.result))
    )
  }

  private def replayOwnerSettlement(
    state: BattleAggregateState,
    settlements: Vector[BattleSettlement]
  ): BattleSettlement =
    state.winnerPlayerId
      .flatMap(winnerPlayerId => settlements.find(_.player.exists(_.playerId == winnerPlayerId)))
      .getOrElse(settlements.head)

  private def serverResult(
    state: BattleAggregateState,
    finishedAt: EpochMillis,
    durationMs: DurationMillis,
    playersLine: String,
    previousRatings: BattlePreviousRatings
  ): BattleResultRecord = {
    val handle = PlayerHandle("server")
    val ratingBefore = previousRatings.ratingBefore(handle)
    BattleResultRecord(
      battleId = state.battleId,
      handle = handle,
      displayName = DisplayName("服务器摘要"),
      finishedAt = finishedAt,
      finishedAtLabel = formatFinishedAt(finishedAt),
      durationMs = durationMs,
      score = Score(0),
      placement = None,
      aliveAtEnd = false,
      ratingBefore = ratingBefore,
      ratingDelta = 0,
      ratingAfter = ratingBefore,
      resultLabel = "对战结束",
      modeLabel = "权威对战",
      mapLabel = "权威竞技场",
      highlightLine = s"权威对战 ${state.battleId.value} 已结束。",
      playersLine = playersLine,
      timelineHint = "服务器已生成权威结算摘要。",
      currentLoadout = None
    )
  }

  private def replaySettlement(result: BattleResultRecord): ReplaySettlementRecord =
    ReplaySettlementRecord(
      handle = result.handle,
      displayName = result.displayName,
      resultLabel = result.resultLabel,
      highlightLine = result.highlightLine,
      score = result.score,
      placement = result.placement,
      ratingBefore = Some(result.ratingBefore),
      ratingDelta = Some(result.ratingDelta),
      ratingAfter = Some(result.ratingAfter),
      aliveAtEnd = result.aliveAtEnd,
      currentLoadout = result.currentLoadout
    )

  private def playersByPlacement(players: Vector[BattlePlayerState]): Vector[BattlePlayerState] =
    players.sortBy { player =>
      if player.alive then (0, -player.score.value.toLong, -player.hp.value, player.seat.value)
      else (1, -player.eliminatedAtMs.map(_.value).getOrElse(-1L), -player.score.value, player.seat.value)
    }

  private def projectedDuration(state: BattleAggregateState): DurationMillis =
    DurationMillis(clampElapsed(state.elapsedMs.value, state.durationMs.value))

  private def projectedFinishedAt(state: BattleAggregateState): EpochMillis = {
    val duration = projectedDuration(state)
    if state.startedAt.value > 0L then EpochMillis(state.startedAt.value + duration.value)
    else state.serverTime
  }

  private def clampElapsed(value: Long, maxValue: Long): Long =
    math.max(0L, math.min(math.max(0L, maxValue), value))

  private def placementScore(placement: Option[Int], playerCount: Int): Int =
    placement match {
      case Some(value) =>
        val placementIndex = math.max(value - 1, 0)
        val maxPlayerIndex = math.max(playerCount - 1, 0)
        PlacementScores.lift(math.min(placementIndex, maxPlayerIndex)).getOrElse(0)
      case None =>
        0
    }

  private def calculateRatingDelta(score: Int, placement: Option[Int], aliveAtEnd: Boolean): Int = {
    val placementFactor = placement.fold(0)(value => math.max(-12, 16 - value * 4))
    val scoreFactor = math.min(6, math.floor(score.toDouble / 2.0).toInt)
    val aliveFactor = if aliveAtEnd then 2 else -1
    placementFactor + scoreFactor + aliveFactor
  }

  private def highlightLine(player: BattlePlayerState, placement: Int, score: Int): String =
    s"${safeDisplayName(player)} 最终排名第 $placement 名，结算得分 $score，击杀 ${player.kills}，剩余生命 ${math.max(0, player.hp.value)}。"

  private def resultLabel(player: BattlePlayerState, placement: Int): String =
    if placement == 1 then "胜者已决"
    else if player.alive then "存活结算"
    else "淘汰结算"

  private def timelineHint(player: BattlePlayerState): String =
    if player.alive then s"${safeDisplayName(player)} 存活到权威对战结束。"
    else
      player.eliminatedAtMs match {
        case Some(eliminatedAtMs) =>
          s"${safeDisplayName(player)} 在 ${math.max(0L, eliminatedAtMs.value / 1000L)} 秒被淘汰。"
        case None =>
          s"${safeDisplayName(player)} 在结束前被淘汰。"
      }

  private def playersLineFor(players: Vector[BattlePlayerState]): String =
    val line = players
      .sortBy(_.seat.value)
      .map(safeDisplayName)
      .filter(_.nonEmpty)
      .mkString(" | ")
    if line.nonEmpty then line else "暂无参赛者"

  private def replayTitle(result: BattleResultRecord): String =
    if result.handle.key == "server" then "权威对战结束"
    else s"权威对战结束 - ${result.displayName.value}"

  private def replayResultLabel(state: BattleAggregateState): String =
    state.winnerPlayerId
      .flatMap(winnerPlayerId => state.players.find(_.playerId == winnerPlayerId))
      .fold("对战结束")(_ => "胜者已决")

  private def safeDisplayName(player: BattlePlayerState): String = {
    val displayName = player.displayName.value.trim
    if displayName.nonEmpty then displayName else player.handle.value.trim
  }

  private def isPlayableHumanPlayer(player: BattlePlayerState): Boolean =
    !player.isBot && HandlePolicy.isPlayableIdentityHandle(safeHandle(player))

  private def safeHandle(player: BattlePlayerState): String = {
    val handle = player.handle.value.trim
    if handle.nonEmpty then handle else player.playerId.value
  }

  private def formatFinishedAt(timestamp: EpochMillis): String =
    TimestampFormatter.format(Instant.ofEpochMilli(timestamp.value))

  private def replayFramesJson(state: BattleAggregateState): ReplayFramesJson = {
    val frameJson =
      if state.replayFrames.nonEmpty then
        normalizeReplayFrames(state.replayFrames, projectedDuration(state))
          .map(frame => replayFrameJson(state, frame))
      else fallbackReplayFrameJson(state)

    ReplayFramesJson(
      frameCount = frameJson.length,
      json = frameJson.mkString("[", ",", "]")
    )
  }

  private def normalizeReplayFrames(
    frames: Vector[BattleReplayFrameState],
    durationMs: DurationMillis
  ): Vector[BattleReplayFrameState] =
    frames
      .map(frame => frame.copy(elapsedMs = ElapsedMillis(clampElapsed(frame.elapsedMs.value, durationMs.value))))
      .sortBy(_.elapsedMs.value)
      .foldLeft(Vector.empty[BattleReplayFrameState]) {
        case (accumulator, frame) if accumulator.lastOption.exists(_.elapsedMs == frame.elapsedMs) =>
          accumulator.dropRight(1) :+ frame
        case (accumulator, frame) =>
          accumulator :+ frame
      }

  private def fallbackReplayFrameJson(state: BattleAggregateState): Vector[String] = {
    val durationMs = projectedDuration(state)
    val finalElapsedMs = durationMs.value
    val eventElapsedMs = state.events
      .map(event => clampElapsed(event.elapsedMs.value, finalElapsedMs))
      .distinct
      .sorted
      .takeRight(4)
    val initialElapsedMs =
      if eventElapsedMs.headOption.contains(0L) then Vector.empty[Long]
      else Vector(0L)
    val frameElapsedMs = (initialElapsedMs ++ eventElapsedMs :+ finalElapsedMs).distinct.sorted

    frameElapsedMs.map(elapsedMs => replayFrameJson(state, ElapsedMillis(elapsedMs), ElapsedMillis(finalElapsedMs)))
  }

  private def replayFrameJson(state: BattleAggregateState, frame: BattleReplayFrameState): String =
    renderObject(
      Vector(
        "elapsedMs" -> frame.elapsedMs.value.toString,
        "worldSize" -> renderVector(state.worldSize),
        "heroes" -> frame.heroes.sortBy(_.seat.value).map(heroFrameJson).mkString("[", ",", "]"),
        "projectiles" -> frame.projectiles.map(projectileFrameJson).mkString("[", ",", "]"),
        "pickups" -> frame.pickups.map(pickupFrameJson).mkString("[", ",", "]"),
        "eventMessages" -> eventMessagesJson(state, frame.elapsedMs)
      )
    )

  private def replayFrameJson(
    state: BattleAggregateState,
    elapsedMs: ElapsedMillis,
    finalElapsedMs: ElapsedMillis
  ): String =
    renderObject(
      Vector(
        "elapsedMs" -> elapsedMs.value.toString,
        "worldSize" -> renderVector(state.worldSize),
        "heroes" -> state.players.sortBy(_.seat.value).map(player => heroFrameJson(player, elapsedMs, finalElapsedMs)).mkString("[", ",", "]"),
        "projectiles" -> (if elapsedMs == finalElapsedMs then state.projectiles.map(projectileFrameJson).mkString("[", ",", "]") else "[]"),
        "pickups" -> state.pickups.map(pickupFrameJson).mkString("[", ",", "]"),
        "eventMessages" -> eventMessagesJson(state, elapsedMs)
      )
    )

  private def heroFrameJson(hero: BattleReplayHeroFrameState): String =
    renderObject(
      Vector(
        "heroId" -> jsonString(hero.heroId.value),
        "displayName" -> jsonString(replayDisplayName(hero.displayName, hero.handle, hero.playerId)),
        "position" -> renderVector(hero.position),
        "hp" -> (if hero.alive then math.max(0, hero.hp.value) else 0).toString,
        "maxHp" -> math.max(1, hero.maxHp.value).toString,
        "alive" -> hero.alive.toString,
        "lifeState" -> jsonString(if hero.alive then "alive" else "dead"),
        "score" -> hero.score.value.toString,
        "facing" -> hero.facing.value.toString,
        "currentWeaponKind" -> jsonString(WeaponKind.wireValue(hero.currentWeaponKind)),
        "eliminatedAtMs" -> hero.eliminatedAtMs.map(_.value.toString).getOrElse("null")
      )
    )

  private def heroFrameJson(
    player: BattlePlayerState,
    elapsedMs: ElapsedMillis,
    finalElapsedMs: ElapsedMillis
  ): String = {
    val aliveAtFrame =
      if elapsedMs == finalElapsedMs then player.alive
      else player.eliminatedAtMs.forall(_.value > elapsedMs.value)
    renderObject(
      Vector(
        "heroId" -> jsonString(player.heroId.value),
        "displayName" -> jsonString(replayDisplayName(player.displayName, player.handle, player.playerId)),
        "position" -> renderVector(player.position),
        "hp" -> (if aliveAtFrame then math.max(0, player.hp.value) else 0).toString,
        "maxHp" -> math.max(1, player.maxHp.value).toString,
        "alive" -> aliveAtFrame.toString,
        "lifeState" -> jsonString(if aliveAtFrame then "alive" else "dead"),
        "score" -> player.score.value.toString,
        "facing" -> player.facing.value.toString,
        "currentWeaponKind" -> jsonString(WeaponKind.wireValue(player.currentWeaponKind)),
        "eliminatedAtMs" -> player.eliminatedAtMs.map(_.value.toString).getOrElse("null")
      )
    )
  }

  private def projectileFrameJson(projectile: BattleReplayProjectileFrameState): String =
    renderObject(
      Vector(
        "projectileId" -> jsonString(projectile.projectileId.value),
        "kind" -> jsonString(ProjectileKind.wireValue(projectile.projectileKind)),
        "position" -> renderVector(projectile.position),
        "facing" -> projectile.facing.value.toString,
        "alive" -> true.toString,
        "ttlMs" -> math.max(0L, projectile.ttlMs.value).toString,
        "splashRadius" -> math.max(0.0, projectile.splashRadius.value).toString
      )
    )

  private def projectileFrameJson(projectile: BattleProjectileState): String =
    renderObject(
      Vector(
        "projectileId" -> jsonString(projectile.projectileId.value),
        "kind" -> jsonString(ProjectileKind.wireValue(projectile.projectileKind)),
        "position" -> renderVector(projectile.position),
        "facing" -> projectile.facing.value.toString,
        "alive" -> true.toString,
        "ttlMs" -> math.max(0L, projectile.ttlMs.value).toString,
        "splashRadius" -> math.max(0.0, projectile.splashRadius.value).toString
      )
    )

  private def pickupFrameJson(pickup: BattleReplayPickupFrameState): String =
    renderObject(
      Vector(
        "id" -> jsonString(pickup.pickupId.value),
        "kind" -> jsonString(replayPickupKind(pickup.pickupKind)),
        "position" -> renderVector(pickup.position),
        "available" -> pickup.available.toString
      ) ++ optionalStringField("weaponKind", pickup.weaponKind.map(WeaponKind.wireValue))
    )

  private def pickupFrameJson(pickup: BattlePickupState): String =
    renderObject(
      Vector(
        "id" -> jsonString(pickup.pickupId.value),
        "kind" -> jsonString(replayPickupKind(pickup.pickupKind)),
        "position" -> renderVector(pickup.position),
        "available" -> pickup.available.toString
      ) ++ optionalStringField("weaponKind", pickup.weaponKind.map(WeaponKind.wireValue))
    )

  private def eventMessagesJson(state: BattleAggregateState, elapsedMs: ElapsedMillis): String =
    state.events
      .filter(_.elapsedMs.value <= elapsedMs.value)
      .sortBy(_.elapsedMs.value)
      .takeRight(6)
      .map(event => jsonString(event.message))
      .mkString("[", ",", "]")

  private def replayDisplayName(displayName: DisplayName, handle: PlayerHandle, playerId: PlayerId): String = {
    val display = displayName.value.trim
    if display.nonEmpty then display
    else
      val handleValue = handle.value.trim
      if handleValue.nonEmpty then handleValue else playerId.value
  }

  private def replayPickupKind(kind: PickupKind): String =
    kind match {
      case PickupKind.Weapon => "weapon"
      case PickupKind.Medkit => "medkit"
    }

  private def renderVector(vector: BattleVector2): String =
    renderObject(Vector("x" -> vector.x.toString, "y" -> vector.y.toString))

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${escapeJson(value)}""""

  private def escapeJson(value: String): String =
    value.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case char if char.isControl => f"\\u${char.toInt}%04x"
      case char => char.toString
    }

  private final case class ReplayFramesJson(frameCount: Int, json: String)

  private[services] val DefaultRating: Rating = Rating(1200)
  private val PlacementScores: Vector[Int] = Vector(12, 9, 7, 5, 3, 1)
}
