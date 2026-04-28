package slaydemo.backend.battle.services

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import scala.collection.concurrent.TrieMap

import slaydemo.backend.battle.api.BattleResultSubmissionRequest
import slaydemo.backend.battle.objects.{
  BattleAggregateState,
  BattlePlayerState,
  BattlePickupState,
  BattleProjectileState,
  BattleReplayFrameState,
  BattleReplayHeroFrameState,
  BattleReplayPickupFrameState,
  BattleReplayProjectileFrameState,
  BattleVector2
}
import slaydemo.backend.replay.api.ReplaySubmissionRequest
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}
import slaydemo.backend.shared.rules.HandleRules

final class AuthoritativeBattleFinishProjector(
  battleResultService: BattleResultService,
  replayService: ReplayService
) {
  private final case class ProjectionState(
    resultRecorded: Boolean,
    replayRecorded: Boolean
  )

  private val projectionByBattleId = TrieMap.empty[String, ProjectionState]
  private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

  def isResultReady(battleId: BattleId): Boolean =
    projectionByBattleId.get(battleId.value.trim).exists(_.resultRecorded)

  def isReplayReady(battleId: BattleId): Boolean =
    projectionByBattleId.get(battleId.value.trim).exists(_.replayRecorded)

  def projectFinishedBattle(state: BattleAggregateState): Unit = {
    if (state.phase != "finished") {
      return
    }

    val battleId = state.battleId.value.trim
    if (battleId.isEmpty) {
      return
    }

    this.synchronized {
      val current = projectionByBattleId.getOrElse(battleId, ProjectionState(resultRecorded = false, replayRecorded = false))
      if (current.resultRecorded && current.replayRecorded) {
        return
      }

      val afterResult =
        if (current.resultRecorded) current
        else recordResult(state, battleId).fold(_ => current, _ => current.copy(resultRecorded = true))

      val afterReplay =
        if (afterResult.replayRecorded) afterResult
        else recordReplay(state, battleId).fold(_ => afterResult, _ => afterResult.copy(replayRecorded = true))

      projectionByBattleId.put(battleId, afterReplay)
    }
  }

  private def recordResult(state: BattleAggregateState, battleId: String): Either[String, Unit] = {
    val summary = buildSummary(state)
    val requests = buildResultRequests(state, battleId, summary)

    catchProjectionError("result", battleId) {
      requests.foldLeft[Either[String, Unit]](Right(())) {
        case (Left(error), _) => Left(error)
        case (Right(_), request) => battleResultService.record(request).map(_ => ())
      }
    }
  }

  private def recordReplay(state: BattleAggregateState, battleId: String): Either[String, Unit] = {
    val summary = buildSummary(state)
    val replayFramesJson = buildReplayFramesJson(state, summary.durationMs)
    val request = ReplaySubmissionRequest(
      replayId = ReplayId(battleId),
      battleId = BattleId(battleId),
      handle = summary.owner.handle,
      displayName = summary.owner.displayName,
      finishedAt = summary.finishedAt,
      finishedAtLabel = formatFinishedAt(summary.finishedAt),
      title = summary.owner.title,
      modeLabel = "权威对战",
      resultLabel = summary.winner.fold("对战结束")(_ => "胜者已决"),
      mapLabel = "权威竞技场",
      highlightLine = summary.owner.highlightLine,
      coverLabel = "服务器战报",
      playersLine = summary.playersLine,
      timelineHint = summary.owner.timelineHint,
      score = summary.owner.score,
      placement = summary.owner.placement,
      durationMs = summary.durationMs,
      aliveAtEnd = summary.owner.aliveAtEnd,
      thumbnailDataUrl = None,
      currentLoadout = None,
      frameCount = replayFramesJson.frameCount,
      playbackAvailable = replayFramesJson.frameCount >= 2,
      framesJson = replayFramesJson.json
    )

    catchProjectionError("replay", battleId) {
      replayService.record(request).map(_ => ())
    }
  }

  private def catchProjectionError(label: String, battleId: String)(write: => Either[String, Unit]): Either[String, Unit] = {
    try {
      write.left.map { error =>
        logFailure(label, battleId, error)
        error
      }
    } catch {
      case error: Throwable =>
        val message = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
        logFailure(label, battleId, message)
        Left(message)
    }
  }

  private def buildSummary(state: BattleAggregateState): FinishSummary = {
    val durationMs = math.max(0L, math.min(state.elapsedMs, state.durationMs))
    val finishedAt =
      if (state.startedAt > 0L) state.startedAt + durationMs
      else math.max(state.serverTime, 0L)
    val winner = state.winnerPlayerId
      .flatMap(winnerPlayerId => state.players.find(_.playerId == winnerPlayerId))
    val playersLine = state.players
      .sortBy(_.seat)
      .map(safeDisplayName)
      .filter(_.nonEmpty)
      .mkString(" | ")
    val rankedPlayers = rankPlayers(state.players)
    val rankedPlayableHumans = rankedPlayers.filter(isPlayableHumanPlayer)
    val playerCount = rankedPlayers.length
    val owner = winner
      .filter(isPlayableHumanPlayer)
      .map(player => winnerSummary(player, state.battleId.value.trim, playerCount))
      .orElse(rankedPlayableHumans.headOption.map(player => playerSummary(player, state.battleId.value.trim, rankedPlayers)))
      .getOrElse(serverSummary(state.battleId.value.trim))

    FinishSummary(
      finishedAt = finishedAt,
      durationMs = durationMs,
      winner = winner,
      owner = owner,
      playersLine = if (playersLine.nonEmpty) playersLine else "暂无参赛者"
    )
  }

  private def buildResultRequests(
    state: BattleAggregateState,
    battleId: String,
    summary: FinishSummary
  ): Seq[BattleResultSubmissionRequest] = {
    val rankedPlayers = rankPlayers(state.players)
    val humanPlayers = rankedPlayers.filter(isPlayableHumanPlayer)
    val owners =
      if (humanPlayers.nonEmpty) humanPlayers.map(player => playerSummary(player, battleId, rankedPlayers))
      else Seq(serverSummary(battleId))

    owners.map { owner =>
      val ratingBefore = latestRatingAfter(owner.handle.value, battleId).getOrElse(1200)
      val ratingDelta = calculateRatingDelta(owner.score, owner.placement, owner.aliveAtEnd)
      BattleResultSubmissionRequest(
        battleId = BattleId(battleId),
        handle = owner.handle,
        displayName = owner.displayName,
        finishedAt = summary.finishedAt,
        finishedAtLabel = formatFinishedAt(summary.finishedAt),
        durationMs = summary.durationMs,
        score = owner.score,
        placement = owner.placement,
        aliveAtEnd = owner.aliveAtEnd,
        ratingBefore = ratingBefore,
        ratingDelta = ratingDelta,
        ratingAfter = ratingBefore + ratingDelta,
        resultLabel = owner.resultLabel,
        modeLabel = "权威对战",
        mapLabel = "权威竞技场",
        highlightLine = owner.highlightLine,
        playersLine = summary.playersLine,
        timelineHint = owner.timelineHint,
        currentLoadout = None
      )
    }
  }

  private def winnerSummary(player: BattlePlayerState, battleId: String, playerCount: Int): SummaryOwner = {
    val displayName = player.displayName.trim
    val displayLabel = if (displayName.nonEmpty) displayName else safeHandle(player)
    val score = placementScore(Some(1), playerCount)
    val kills = player.kills
    SummaryOwner(
      handle = UserId(safeHandle(player)),
      displayName = displayLabel,
      score = score,
      placement = Some(1),
      aliveAtEnd = true,
      title = s"权威对战结束 - $displayLabel",
      resultLabel = "胜者已决",
      highlightLine = s"$displayLabel 赢得权威对战，结算得分 $score，击杀 $kills。",
      timelineHint = "服务器已生成胜者战报。"
    )
  }

  private def playerSummary(player: BattlePlayerState, battleId: String, ranking: Seq[BattlePlayerState]): SummaryOwner = {
    val displayName = player.displayName.trim
    val displayLabel = if (displayName.nonEmpty) displayName else safeHandle(player)
    val placement = ranking.indexWhere(_.playerId == player.playerId) match {
      case index if index >= 0 => Some(index + 1)
      case _                   => None
    }
    val score = placementScore(placement, ranking.length)
    val placementLabel = placement.map(value => s"第 $value 名").getOrElse("完成")
    val resultLabel =
      if (placement.contains(1)) "胜者已决"
      else if (player.alive) "存活结算"
      else "淘汰结算"
    val timelineHint =
      if (player.alive) s"$displayLabel 存活到权威对战结束。"
      else player.eliminatedAtMs match {
        case Some(eliminatedAtMs) => s"$displayLabel 在 ${math.max(0L, eliminatedAtMs / 1000L)} 秒被淘汰。"
        case None                 => s"$displayLabel 在结束前被淘汰。"
      }

    SummaryOwner(
      handle = UserId(safeHandle(player)),
      displayName = displayLabel,
      score = score,
      placement = placement,
      aliveAtEnd = player.alive,
      title = s"权威对战结束 - $displayLabel",
      resultLabel = resultLabel,
      highlightLine = s"$displayLabel 最终排名$placementLabel，结算得分 $score，击杀 ${player.kills}，剩余生命 ${player.hp.max(0)}。",
      timelineHint = timelineHint
    )
  }

  private def serverSummary(battleId: String): SummaryOwner =
    SummaryOwner(
      handle = UserId("server"),
      displayName = "服务器摘要",
      score = 0,
      placement = None,
      aliveAtEnd = false,
      title = "权威对战结束",
      resultLabel = "对战结束",
      highlightLine = s"权威对战 $battleId 已结束。",
      timelineHint = "服务器已生成权威结算摘要。"
    )

  private def rankPlayers(players: Seq[BattlePlayerState]): Seq[BattlePlayerState] =
    players.sortBy { player =>
      if (player.alive) {
        (0, -player.score.toLong, -player.hp, player.seat)
      } else {
        (1, -player.eliminatedAtMs.getOrElse(-1L), -player.score, player.seat)
      }
    }

  private def latestRatingAfter(handle: String, currentBattleId: String): Option[Int] =
    battleResultService
      .list(Some(handle), None, 50)
      .find(record => record.battleId.value.trim != currentBattleId.trim)
      .map(_.ratingAfter)

  private def placementScore(placement: Option[Int], playerCount: Int): Int = {
    val ladder = Vector(12, 9, 7, 5, 3, 1)
    placement match {
      case Some(value) =>
        val placementIndex = math.max(value - 1, 0)
        val maxPlayerIndex = math.max(playerCount - 1, 0)
        ladder.lift(math.min(placementIndex, maxPlayerIndex)).getOrElse(0)
      case None =>
        0
    }
  }

  private def calculateRatingDelta(score: Int, placement: Option[Int], aliveAtEnd: Boolean): Int = {
    val placementFactor = placement.fold(0)(value => math.max(-12, 16 - value * 4))
    val scoreFactor = math.min(6, math.floor(score.toDouble / 2.0).toInt)
    val aliveFactor = if (aliveAtEnd) 2 else -1
    placementFactor + scoreFactor + aliveFactor
  }

  private def buildReplayFramesJson(state: BattleAggregateState, durationMs: Long): ReplayFramesJson = {
    val frames =
      if (state.replayFrames.nonEmpty) {
        normalizeReplayFrames(state.replayFrames, durationMs).map(frame => renderReplayFrame(state, frame))
      } else {
        buildFallbackReplayFramesJson(state, durationMs)
      }

    ReplayFramesJson(
      frameCount = frames.length,
      json = frames.mkString("[", ",", "]")
    )
  }

  private def buildFallbackReplayFramesJson(state: BattleAggregateState, durationMs: Long): Vector[String] = {
    val finalElapsedMs = math.max(0L, math.min(state.elapsedMs, durationMs))
    val eventElapsedMs = state.events
      .map(event => math.max(0L, math.min(event.elapsedMs, finalElapsedMs)))
      .distinct
      .sorted
      .takeRight(4)
    val summaryElapsedMs =
      if (eventElapsedMs.headOption.contains(0L)) Vector.empty[Long]
      else Vector(0L)
    val frameElapsedMs = (summaryElapsedMs ++ eventElapsedMs :+ finalElapsedMs).distinct.sorted
    frameElapsedMs.map(elapsedMs => renderFallbackReplayFrame(state, elapsedMs, finalElapsedMs))
  }

  private def normalizeReplayFrames(
    frames: Vector[BattleReplayFrameState],
    durationMs: Long
  ): Vector[BattleReplayFrameState] = {
    val finalElapsedMs = math.max(0L, durationMs)
    frames
      .map(frame => frame.copy(elapsedMs = math.max(0L, math.min(frame.elapsedMs, finalElapsedMs))))
      .sortBy(_.elapsedMs)
      .foldLeft(Vector.empty[BattleReplayFrameState]) {
        case (accumulator, frame) if accumulator.lastOption.exists(_.elapsedMs == frame.elapsedMs) =>
          accumulator.dropRight(1) :+ frame
        case (accumulator, frame) =>
          accumulator :+ frame
      }
  }

  private def renderReplayFrame(state: BattleAggregateState, frame: BattleReplayFrameState): String = {
    val heroes = frame.heroes.sortBy(_.seat).map(hero => renderReplayHero(hero)).mkString(",")
    val projectiles = frame.projectiles.map(projectile => renderReplayProjectile(projectile)).mkString(",")
    val pickups = frame.pickups.map(pickup => renderReplayPickup(pickup)).mkString(",")
    val eventMessages = renderReplayEventMessages(state, frame.elapsedMs)

    s"""{"elapsedMs":${frame.elapsedMs},"worldSize":${renderReplayVector(state.worldSize)},"heroes":[$heroes],"projectiles":[$projectiles],"pickups":[$pickups],"eventMessages":[$eventMessages]}"""
  }

  private def renderFallbackReplayFrame(state: BattleAggregateState, elapsedMs: Long, finalElapsedMs: Long): String = {
    val heroes = state.players.sortBy(_.seat).map(player => renderReplayHero(player, elapsedMs, finalElapsedMs)).mkString(",")
    val projectiles =
      if (elapsedMs == finalElapsedMs) state.projectiles.map(projectile => renderReplayProjectile(projectile)).mkString(",")
      else ""
    val pickups = state.pickups.map(pickup => renderReplayPickup(pickup)).mkString(",")
    val eventMessages = renderReplayEventMessages(state, elapsedMs)

    s"""{"elapsedMs":$elapsedMs,"worldSize":${renderReplayVector(state.worldSize)},"heroes":[$heroes],"projectiles":[$projectiles],"pickups":[$pickups],"eventMessages":[$eventMessages]}"""
  }

  private def renderReplayHero(hero: BattleReplayHeroFrameState): String = {
    val aliveAtFrame = hero.alive
    val hpAtFrame = if (aliveAtFrame) math.max(0, hero.hp) else 0
    val lifeState = if (aliveAtFrame) "alive" else "dead"
    val displayName = safeReplayDisplayName(hero)
    val weaponKind = Option(hero.currentWeaponKind).map(_.trim).filter(_.nonEmpty).getOrElse("Pistol")
    val eliminatedAtMs = hero.eliminatedAtMs.map(value => math.max(0L, value))

    s"""{"heroId":"${escapeJson(hero.heroId)}","displayName":"${escapeJson(displayName)}","position":${renderReplayVector(hero.position)},"hp":$hpAtFrame,"maxHp":${math.max(1, hero.maxHp)},"alive":$aliveAtFrame,"lifeState":"$lifeState","score":${hero.score},"facing":${hero.facing},"currentWeaponKind":"${escapeJson(weaponKind)}","eliminatedAtMs":${renderOptionalLong(eliminatedAtMs)}}"""
  }

  private def renderReplayHero(player: BattlePlayerState, elapsedMs: Long, finalElapsedMs: Long): String = {
    val eliminatedAtMs = player.eliminatedAtMs.map(value => math.max(0L, value))
    val aliveAtFrame =
      if (elapsedMs == finalElapsedMs) player.alive
      else eliminatedAtMs.forall(_ > elapsedMs)
    val hpAtFrame =
      if (aliveAtFrame) math.max(0, player.hp)
      else 0
    val lifeState = if (aliveAtFrame) "alive" else "dead"
    val displayName = safeDisplayName(player) match {
      case value if value.nonEmpty => value
      case _                       => safeHandle(player)
    }

    s"""{"heroId":"${escapeJson(player.heroId)}","displayName":"${escapeJson(displayName)}","position":${renderReplayVector(player.position)},"hp":$hpAtFrame,"maxHp":${math.max(1, player.maxHp)},"alive":$aliveAtFrame,"lifeState":"$lifeState","score":${player.score},"facing":${player.facing},"currentWeaponKind":"Pistol","eliminatedAtMs":${renderOptionalLong(eliminatedAtMs)}}"""
  }

  private def renderReplayProjectile(projectile: BattleReplayProjectileFrameState): String = {
    val kind = normalizeReplayProjectileKind(projectile.kind)
    s"""{"projectileId":"${escapeJson(projectile.projectileId)}","kind":"$kind","position":${renderReplayVector(projectile.position)},"facing":${projectile.facing},"alive":true,"ttlMs":${math.max(0L, projectile.ttlMs)},"splashRadius":${math.max(0.0, projectile.splashRadius)}}"""
  }

  private def renderReplayProjectile(projectile: BattleProjectileState): String = {
    val kind = normalizeReplayProjectileKind(projectile.kind)
    s"""{"projectileId":"${escapeJson(projectile.projectileId)}","kind":"$kind","position":${renderReplayVector(projectile.position)},"facing":${projectile.facing},"alive":true,"ttlMs":${math.max(0L, projectile.ttlMs)},"splashRadius":${math.max(0.0, projectile.splashRadius)}}"""
  }

  private def renderReplayPickup(pickup: BattleReplayPickupFrameState): String = {
    val kind = normalizeReplayPickupKind(pickup.kind)
    val weaponKind = renderReplayPickupWeaponKind(kind, pickup.weaponKind)
    s"""{"id":"${escapeJson(pickup.pickupId)}","kind":"$kind"$weaponKind,"position":${renderReplayVector(pickup.position)},"available":${pickup.available}}"""
  }

  private def renderReplayPickup(pickup: BattlePickupState): String = {
    val kind = normalizeReplayPickupKind(pickup.kind)
    val weaponKind = renderReplayPickupWeaponKind(kind, pickup.weaponKind)
    s"""{"id":"${escapeJson(pickup.pickupId)}","kind":"$kind"$weaponKind,"position":${renderReplayVector(pickup.position)},"available":${pickup.available}}"""
  }

  private def renderReplayPickupWeaponKind(kind: String, weaponKind: Option[String]): String =
    if (kind == "weapon") {
      val normalized = weaponKind.map(_.trim).filter(_.nonEmpty).getOrElse("Pistol")
      ",\"weaponKind\":\"" + escapeJson(normalized) + "\""
    } else {
      ""
    }

  private def renderReplayEventMessages(state: BattleAggregateState, elapsedMs: Long): String =
    state.events
      .filter(event => event.elapsedMs <= elapsedMs)
      .sortBy(_.elapsedMs)
      .takeRight(6)
      .map(event => "\"" + escapeJson(event.message.trim) + "\"")
      .mkString(",")

  private def normalizeReplayProjectileKind(kind: String): String =
    kind match {
      case "rocket" | "gatling-bullet" | "shotgun-pellet" | "pistol-bullet" => kind
      case _                                                                 => "pistol-bullet"
    }

  private def normalizeReplayPickupKind(kind: String): String =
    Option(kind).map(_.trim.toLowerCase).getOrElse("") match {
      case "weapon" => "weapon"
      case _        => "medkit"
    }

  private def renderReplayVector(vector: BattleVector2): String =
    s"""{"x":${vector.x},"y":${vector.y}}"""

  private def renderOptionalLong(value: Option[Long]): String =
    value.map(_.toString).getOrElse("null")

  private def escapeJson(value: String): String =
    Option(value).getOrElse("")
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def safeHandle(player: BattlePlayerState): String = {
    val handle = player.handle.trim
    if (handle.nonEmpty) handle else player.playerId.value
  }

  private def isPlayableHumanPlayer(player: BattlePlayerState): Boolean =
    !player.isBot && HandleRules.isPlayableIdentityHandle(safeHandle(player))

  private def safeDisplayName(player: BattlePlayerState): String = {
    val displayName = player.displayName.trim
    if (displayName.nonEmpty) displayName else player.handle.trim
  }

  private def safeReplayDisplayName(hero: BattleReplayHeroFrameState): String = {
    val displayName = hero.displayName.trim
    if (displayName.nonEmpty) displayName
    else {
      val handle = hero.handle.trim
      if (handle.nonEmpty) handle else hero.playerId.value
    }
  }

  private def formatFinishedAt(timestamp: Long): String =
    timestampFormatter.format(Instant.ofEpochMilli(timestamp))

  private def logFailure(label: String, battleId: String, error: String): Unit =
    Console.err.println(s"[authoritative-battle-finish-projector] failed to record $label for $battleId: $error")

  private final case class FinishSummary(
    finishedAt: Long,
    durationMs: Long,
    winner: Option[BattlePlayerState],
    owner: SummaryOwner,
    playersLine: String
  )

  private final case class ReplayFramesJson(
    frameCount: Int,
    json: String
  )

  private final case class SummaryOwner(
    handle: UserId,
    displayName: String,
    score: Int,
    placement: Option[Int],
    aliveAtEnd: Boolean,
    title: String,
    resultLabel: String,
    highlightLine: String,
    timelineHint: String
  )
}
