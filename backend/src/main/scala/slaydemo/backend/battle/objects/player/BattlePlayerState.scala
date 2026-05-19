package slaydemo.backend.battle.objects.player

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.objects.core.*
import slaydemo.backend.battle.objects.event.*
import slaydemo.backend.battle.objects.pickup.*
import slaydemo.backend.battle.objects.player.*
import slaydemo.backend.battle.objects.projectile.*
import slaydemo.backend.battle.objects.queue.*
import slaydemo.backend.battle.objects.replay.*
import slaydemo.backend.battle.objects.result.*
import slaydemo.backend.battle.objects.skill.*
import slaydemo.backend.battle.objects.weapon.*

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class BattlePlayerSkillState(
  skillKind: SkillKind,
  cooldownMs: CooldownMillis,
  activeMs: DurationMillis
)

final case class BattlePlayerState(
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  seat: SeatIndex,
  participantKind: BattleParticipantKind,
  position: BattleVector2,
  aim: BattleVector2,
  facing: FacingRadians,
  movement: BattleVector2,
  sprint: Boolean,
  primaryHeld: Boolean,
  reloadPressed: Boolean,
  lastClientCommandSeq: ClientCommandSeq,
  currentWeaponIndex: Int,
  weapons: Vector[BattleWeaponState],
  currentWeaponKind: WeaponKind,
  hp: HitPoints,
  maxHp: HitPoints,
  stamina: Stamina,
  maxStamina: Stamina,
  score: Score,
  kills: Int,
  skills: Vector[BattlePlayerSkillState],
  lifeState: BattlePlayerLifeState
) {
  /**
   * 中文名：是否存活（alive）。
   * 游戏视线：这是玩家生命状态的只读展开字段；true 表示玩家还在战斗中，false 表示已被淘汰或等待复活。
   */
  def alive: Boolean =
    BattlePlayerLifeState.aliveFlag(lifeState)

  /**
   * 中文名：淘汰时刻毫秒（eliminatedAtMs）。
   * 游戏视线：ElapsedMillis 是从本局开始计算的毫秒时间点；只有玩家被淘汰后才会有值。
   */
  def eliminatedAtMs: Option[ElapsedMillis] =
    BattlePlayerLifeState.eliminatedAtMs(lifeState)

  /**
   * 中文名：复活剩余毫秒（respawnMs）。
   * 游戏视线：DurationMillis 是毫秒单位值对象；在玩家状态里表示淘汰后距离重新出生还剩多久，存活时为 0。
   */
  def respawnMs: DurationMillis =
    BattlePlayerLifeState.respawnMs(lifeState)

  /**
   * 中文名：是否机器人（isBot）。
   * 游戏视线：读取参与者类型的展开字段；Bot 玩家由后端 bot 规则驱动，Human 玩家由客户端命令驱动。
   */
  def isBot: Boolean =
    BattleParticipantKind.isBot(participantKind)
}
