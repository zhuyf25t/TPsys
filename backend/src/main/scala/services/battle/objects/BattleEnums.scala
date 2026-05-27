package services.battle.objects

import services.battle.objects.core.{BattleMapId, BattleMapLabel, BattleModeLabel}

enum MatchmakingRoomPhase {
  case Waiting
  case Active
  case Finished
  case Unknown
}

object MatchmakingRoomPhase {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把匹配房间阶段输出为 HTTP/JSON 使用的小写字符串，前端等待区据此显示“等待中、已开局、已结束”等状态。
   * 建模原因：房间阶段本身是有限状态枚举，序列化出口集中在这里可以避免直接暴露 Scala 枚举名。
   */
  def wireValue(value: MatchmakingRoomPhase): String =
    value match {
      case MatchmakingRoomPhase.Waiting  => "waiting"
      case MatchmakingRoomPhase.Active   => "active"
      case MatchmakingRoomPhase.Finished => "finished"
      case MatchmakingRoomPhase.Unknown  => "unknown"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把前端、回放或持久化层传入的房间阶段字符串读回后端枚举，供排队和房间快照逻辑使用。
   * 建模原因：反序列化失败返回 None，让调用方显式处理坏数据，避免未知房间阶段进入匹配状态机。
   */
  def fromWire(value: String): Option[MatchmakingRoomPhase] =
    value match {
      case "waiting"  => Some(MatchmakingRoomPhase.Waiting)
      case "active"   => Some(MatchmakingRoomPhase.Active)
      case "finished" => Some(MatchmakingRoomPhase.Finished)
      case "unknown"  => Some(MatchmakingRoomPhase.Unknown)
      case _          => None
    }
}

enum BattleMode {
  case Default
  case Autumn
  case Winter
  case Normal
}

object BattleMode {
  def default: BattleMode =
    BattleMode.Default

  def wireValue(value: BattleMode): String =
    value match {
      case BattleMode.Default => "default"
      case BattleMode.Autumn  => "autumn"
      case BattleMode.Winter  => "winter"
      case BattleMode.Normal  => "normal"
    }

  def fromWire(value: String): Option[BattleMode] =
    Option(value).map(_.trim.toLowerCase).flatMap {
      case "default" | "default-mode" | "default_mode" =>
        Some(BattleMode.Default)
      case "autumn" | "fall" | "fall-hunt" | "fall_hunt" =>
        Some(BattleMode.Autumn)
      case "winter" | "winter-hunt" | "winter_hunt" =>
        Some(BattleMode.Winter)
      case "normal" | "normal-hunt" | "normal_hunt" =>
        Some(BattleMode.Normal)
      case _ =>
        None
    }

  def mapId(value: BattleMode): BattleMapId =
    value match {
      case BattleMode.Default => BattleMapId("default-industrial-arena")
      case BattleMode.Autumn  => BattleMapId("fall-hunt-v1")
      case BattleMode.Winter  => BattleMapId("winter-hunt-v1")
      case BattleMode.Normal  => BattleMapId("normal-hunt-v1")
    }

  def modeLabel(value: BattleMode): BattleModeLabel =
    BattleModeLabel.fromWire(
      value match {
        case BattleMode.Default => "默认模式"
        case BattleMode.Autumn  => "秋季模式"
        case BattleMode.Winter  => "冬季模式"
        case BattleMode.Normal  => "普通模式"
      }
    )

  def mapLabel(value: BattleMode): BattleMapLabel =
    BattleMapLabel.fromWire(
      value match {
        case BattleMode.Default => "默认地图"
        case BattleMode.Autumn  => "秋季地图"
        case BattleMode.Winter  => "冬季地图"
        case BattleMode.Normal  => "普通地图"
      }
    )
}

enum BattlePhase {
  case Waiting
  case Active
  case Finished
}

object BattlePhase {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把战斗阶段输出为前端状态字段，表示战斗还在等待、进行中还是已经结束。
   * 建模原因：后端运行时使用 `BattlePhase` 枚举推进状态机，JSON 边界只通过这个函数拿稳定字符串。
   */
  def wireValue(value: BattlePhase): String =
    value match {
      case BattlePhase.Waiting  => "waiting"
      case BattlePhase.Active   => "active"
      case BattlePhase.Finished => "finished"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把战斗阶段字符串读回后端枚举，用于回放读取、测试输入或未来的状态恢复。
   * 建模原因：解析失败返回 None，避免非法字符串伪装成合法战斗阶段。
   */
  def fromWire(value: String): Option[BattlePhase] =
    value match {
      case "waiting"  => Some(BattlePhase.Waiting)
      case "active"   => Some(BattlePhase.Active)
      case "finished" => Some(BattlePhase.Finished)
      case _          => None
    }
}

enum BattleArtifactStatus {
  case Pending
  case ResultOnlyReady
  case ReplayOnlyReady
  case Ready
}

object BattleArtifactStatus {
  /**
   * 中文名：结果是否就绪（isResultReady）。
   * 游戏视线：战斗结束后，结算页需要知道战报 JSON 是否已经生成，可以被玩家历史记录读取。
   * 建模原因：`BattleArtifactStatus` 同时表达战报和回放两个产物状态，这里只投影“战报是否 ready”这一事实。
   */
  def isResultReady(value: BattleArtifactStatus): Boolean =
    value match {
      case BattleArtifactStatus.Pending         => false
      case BattleArtifactStatus.ResultOnlyReady => true
      case BattleArtifactStatus.ReplayOnlyReady => false
      case BattleArtifactStatus.Ready           => true
    }

  /**
   * 中文名：回放是否就绪（isReplayReady）。
   * 游戏视线：回放入口需要知道 replay frames 是否已经写完，避免玩家打开半成品回放。
   * 建模原因：`BattleArtifactStatus` 是组合状态枚举，这个函数把“回放 ready”从组合状态里安全投影出来。
   */
  def isReplayReady(value: BattleArtifactStatus): Boolean =
    value match {
      case BattleArtifactStatus.Pending         => false
      case BattleArtifactStatus.ResultOnlyReady => false
      case BattleArtifactStatus.ReplayOnlyReady => true
      case BattleArtifactStatus.Ready           => true
    }

  /**
   * 中文名：从就绪布尔值创建产物状态（fromReadiness）。
   * 游戏视线：战斗收尾会分别生成结算结果和回放文件，这里把两个完成标记压缩成一个产物状态。
   * 建模原因：把 `resultReady/replayReady` 两个 Boolean 集中转换为有限枚举，避免调用方拼出互相矛盾的字符串状态。
   */
  def fromReadiness(resultReady: Boolean, replayReady: Boolean): BattleArtifactStatus =
    (resultReady, replayReady) match {
      case (false, false) => BattleArtifactStatus.Pending
      case (true, false)  => BattleArtifactStatus.ResultOnlyReady
      case (false, true)  => BattleArtifactStatus.ReplayOnlyReady
      case (true, true)   => BattleArtifactStatus.Ready
    }

  /**
   * 中文名：合并产物状态（merge）。
   * 游戏视线：战斗收尾可能分多步写入战报和回放，合并时任何一边已经 ready 都必须保留 ready，不允许状态回退。
   * 建模原因：用 `fromReadiness` 重新归一化组合状态，避免手写状态合并遗漏 ResultOnlyReady 或 ReplayOnlyReady。
   */
  def merge(current: BattleArtifactStatus, update: BattleArtifactStatus): BattleArtifactStatus =
    fromReadiness(
      isResultReady(current) || isResultReady(update),
      isReplayReady(current) || isReplayReady(update)
    )
}

enum WeaponKind {
  case Pistol
  case RocketLauncher
  case Gatling
  case Shotgun
}

object WeaponKind {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把武器类型输出为前端背包、拾取物、HUD 和回放共同使用的武器名称。
   * 建模原因：武器是有限枚举，集中序列化能保证后端 `WeaponKind` 和前端武器配置使用同一套名字。
   */
  def wireValue(value: WeaponKind): String =
    value match {
      case WeaponKind.Pistol         => "Pistol"
      case WeaponKind.RocketLauncher => "RocketLauncher"
      case WeaponKind.Gatling        => "Gatling"
      case WeaponKind.Shotgun        => "Shotgun"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把请求、回放或存档中的武器字符串还原为后端武器枚举，供开火、换枪和拾取逻辑使用。
   * 建模原因：未知武器返回 None，避免未校验裸字符串进入伤害、弹药和冷却计算。
   */
  def fromWire(value: String): Option[WeaponKind] =
    value match {
      case "Pistol"         => Some(WeaponKind.Pistol)
      case "RocketLauncher" => Some(WeaponKind.RocketLauncher)
      case "Gatling"        => Some(WeaponKind.Gatling)
      case "Shotgun"        => Some(WeaponKind.Shotgun)
      case _                => None
    }
}

enum ProjectileKind {
  case PistolBullet
  case Rocket
  case GatlingBullet
  case ShotgunPellet
}

object ProjectileKind {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把投射物类型输出为渲染器能识别的子弹/火箭/霰弹弹丸名称，用于选择弹道特效和命中表现。
   * 建模原因：投射物类型决定速度、碰撞、伤害和视觉表现，集中序列化避免前后端使用不同弹种字符串。
   */
  def wireValue(value: ProjectileKind): String =
    value match {
      case ProjectileKind.PistolBullet  => "pistol-bullet"
      case ProjectileKind.Rocket        => "rocket"
      case ProjectileKind.GatlingBullet => "gatling-bullet"
      case ProjectileKind.ShotgunPellet => "shotgun-pellet"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把投射物字符串读回后端枚举，用于回放、命中判定和弹丸终止日志恢复。
   * 建模原因：解析失败返回 None，避免错误弹种进入碰撞和伤害规则。
   */
  def fromWire(value: String): Option[ProjectileKind] =
    value match {
      case "pistol-bullet"  => Some(ProjectileKind.PistolBullet)
      case "rocket"         => Some(ProjectileKind.Rocket)
      case "gatling-bullet" => Some(ProjectileKind.GatlingBullet)
      case "shotgun-pellet" => Some(ProjectileKind.ShotgunPellet)
      case _                => None
    }
}

enum SkillKind {
  case Blink
  case Dash
  case Freeze
}

object SkillKind {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把技能类型输出为前端技能栏、玩家指令和技能结果共同使用的技能名。
   * 建模原因：Blink、Dash、Freeze 是有限技能集合，集中序列化能防止技能命令和 UI 名字漂移。
   */
  def wireValue(value: SkillKind): String =
    value match {
      case SkillKind.Blink  => "Blink"
      case SkillKind.Dash   => "Dash"
      case SkillKind.Freeze => "Freeze"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把前端传入的技能名称还原为后端枚举，供技能冷却、距离和目标合法性规则处理。
   * 建模原因：未知技能返回 None，保证技能命令只接受仓库中定义过的技能类型。
   */
  def fromWire(value: String): Option[SkillKind] =
    value match {
      case "Blink"  => Some(SkillKind.Blink)
      case "Dash"   => Some(SkillKind.Dash)
      case "Freeze" => Some(SkillKind.Freeze)
      case _        => None
    }
}

enum BattleCommandStatus {
  case Applied
  case Ignored
}

object BattleCommandStatus {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把玩家指令处理结果输出为 applied/ignored，告诉前端本帧输入是否被服务器权威状态采纳。
   * 建模原因：命令结果是有限状态，集中序列化避免路由层直接拼写状态字符串。
   */
  def wireValue(value: BattleCommandStatus): String =
    value match {
      case BattleCommandStatus.Applied => "applied"
      case BattleCommandStatus.Ignored => "ignored"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把命令结果字符串读回后端枚举，用于回放、测试或未来命令日志重放。
   * 建模原因：解析失败返回 None，避免任意文本被当成合法命令状态。
   */
  def fromWire(value: String): Option[BattleCommandStatus] =
    value match {
      case "applied" => Some(BattleCommandStatus.Applied)
      case "ignored" => Some(BattleCommandStatus.Ignored)
      case _         => None
    }
}

enum BattleCommandReason {
  case BattleFinished
  case BattleInactive
  case PlayerDead
}

object BattleCommandReason {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把命令被忽略的业务原因输出为稳定字符串，例如战斗已结束、战斗未激活或玩家已死亡。
   * 建模原因：失败原因是有限集合，不能用自由文本承载，否则前端无法可靠区分提示和调试信息。
   */
  def wireValue(value: BattleCommandReason): String =
    value match {
      case BattleCommandReason.BattleFinished => "battle_finished"
      case BattleCommandReason.BattleInactive => "battle_inactive"
      case BattleCommandReason.PlayerDead     => "player_dead"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把命令失败原因字符串恢复成后端枚举，便于命令日志、测试断言或回放诊断使用。
   * 建模原因：未知原因返回 None，防止随意字符串伪装成业务分支。
   */
  def fromWire(value: String): Option[BattleCommandReason] =
    value match {
      case "battle_finished" => Some(BattleCommandReason.BattleFinished)
      case "battle_inactive" => Some(BattleCommandReason.BattleInactive)
      case "player_dead"     => Some(BattleCommandReason.PlayerDead)
      case _                 => None
    }
}

enum SkillOutcomeStatus {
  case Applied
  case Noop
}

object SkillOutcomeStatus {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把技能执行结果输出为 applied/noop，表示技能是否真正改变了战斗状态。
   * 建模原因：技能执行结果是有限状态，集中序列化让前端反馈、日志和回放使用同一契约。
   */
  def wireValue(value: SkillOutcomeStatus): String =
    value match {
      case SkillOutcomeStatus.Applied => "applied"
      case SkillOutcomeStatus.Noop    => "noop"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把技能结果字符串恢复为后端枚举，用于回放和技能诊断。
   * 建模原因：解析失败返回 None，避免不认识的状态进入技能结果处理。
   */
  def fromWire(value: String): Option[SkillOutcomeStatus] =
    value match {
      case "applied" => Some(SkillOutcomeStatus.Applied)
      case "noop"    => Some(SkillOutcomeStatus.Noop)
      case _         => None
    }
}

enum SkillOutcomeReason {
  case SkillNotOwned
  case Cooldown
  case MissingTarget
  case OutOfRange
  case InvalidTarget
  case NoDirection
  case Blocked
}

object SkillOutcomeReason {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把技能没有生效的原因输出为稳定字符串，例如冷却中、缺少目标、目标非法或路径被阻挡。
   * 建模原因：失败原因影响前端提示和调试，不应隐藏在自由文本或 Boolean 里。
   */
  def wireValue(value: SkillOutcomeReason): String =
    value match {
      case SkillOutcomeReason.SkillNotOwned  => "skill_not_owned"
      case SkillOutcomeReason.Cooldown       => "cooldown"
      case SkillOutcomeReason.MissingTarget  => "missing_target"
      case SkillOutcomeReason.OutOfRange     => "out_of_range"
      case SkillOutcomeReason.InvalidTarget  => "invalid_target"
      case SkillOutcomeReason.NoDirection    => "no_direction"
      case SkillOutcomeReason.Blocked        => "blocked"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把技能失败原因字符串恢复为后端枚举，供技能回放、调试面板和测试断言使用。
   * 建模原因：未知失败原因返回 None，保证技能失败分支由 ADT/enum 明确定义。
   */
  def fromWire(value: String): Option[SkillOutcomeReason] =
    value match {
      case "skill_not_owned" => Some(SkillOutcomeReason.SkillNotOwned)
      case "cooldown"        => Some(SkillOutcomeReason.Cooldown)
      case "missing_target"  => Some(SkillOutcomeReason.MissingTarget)
      case "out_of_range"    => Some(SkillOutcomeReason.OutOfRange)
      case "invalid_target"  => Some(SkillOutcomeReason.InvalidTarget)
      case "no_direction"    => Some(SkillOutcomeReason.NoDirection)
      case "blocked"         => Some(SkillOutcomeReason.Blocked)
      case _                 => None
    }
}

enum PickupKind {
  case Medkit
  case Weapon
}

object PickupKind {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把拾取物类型输出为前端地图和 HUD 使用的名字，用来区分药包和武器补给。
   * 建模原因：拾取物类型是有限集合，集中序列化能保证地图资源、后端状态和前端渲染保持同名。
   */
  def wireValue(value: PickupKind): String =
    value match {
      case PickupKind.Medkit => "Medkit"
      case PickupKind.Weapon => "Weapon"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把地图资源或回放中的拾取物类型字符串恢复成后端枚举，供拾取规则和刷新规则使用。
   * 建模原因：未知拾取物返回 None，避免地图 manifest 中的错误字符串进入战斗运行时。
   */
  def fromWire(value: String): Option[PickupKind] =
    value match {
      case "Medkit" => Some(PickupKind.Medkit)
      case "Weapon" => Some(PickupKind.Weapon)
      case _        => None
    }
}

enum ProjectileTerminalReason {
  case Hit
  case Expired
  case Blocked
  case OutOfBounds
}

object ProjectileTerminalReason {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把投射物终止原因输出为 hit/ttl/obstacle/world，用来描述子弹因命中、过期、撞障碍或出界而消失。
   * 建模原因：终止原因影响命中特效和调试日志，集中序列化可以避免前后端对同一种原因使用不同名字。
   */
  def wireValue(value: ProjectileTerminalReason): String =
    value match {
      case ProjectileTerminalReason.Hit         => "hit"
      case ProjectileTerminalReason.Expired     => "ttl"
      case ProjectileTerminalReason.Blocked     => "obstacle"
      case ProjectileTerminalReason.OutOfBounds => "world"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把弹丸终止原因字符串恢复为后端枚举，用于回放读取、命中特效复现和战斗调试。
   * 建模原因：未知原因返回 None，防止自由文本进入 projectile 终止状态。
   */
  def fromWire(value: String): Option[ProjectileTerminalReason] =
    value match {
      case "hit"      => Some(ProjectileTerminalReason.Hit)
      case "ttl"      => Some(ProjectileTerminalReason.Expired)
      case "obstacle" => Some(ProjectileTerminalReason.Blocked)
      case "world"    => Some(ProjectileTerminalReason.OutOfBounds)
      case _          => None
    }
}

enum BattleEventKind {
  case Kill
  case Heal
  case Pickup
  case Respawn
}

object BattleEventKind {
  /**
   * 中文名：协议值（wireValue）。
   * 游戏视线：把战斗事件类型输出为前端事件流使用的字符串，例如击杀、治疗、拾取和复活。
   * 建模原因：事件类型是 HUD、回放和战斗日志共同消费的契约，必须从枚举统一导出。
   */
  def wireValue(value: BattleEventKind): String =
    value match {
      case BattleEventKind.Kill    => "kill"
      case BattleEventKind.Heal    => "heal"
      case BattleEventKind.Pickup  => "pickup"
      case BattleEventKind.Respawn => "respawn"
    }

  /**
   * 中文名：从协议值还原（fromWire）。
   * 游戏视线：把事件流字符串恢复成后端枚举，供回放、历史日志和测试读取。
   * 建模原因：未知事件返回 None，避免未定义事件类型混入战斗事件流。
   */
  def fromWire(value: String): Option[BattleEventKind] =
    value match {
      case "kill"    => Some(BattleEventKind.Kill)
      case "heal"    => Some(BattleEventKind.Heal)
      case "pickup"  => Some(BattleEventKind.Pickup)
      case "respawn" => Some(BattleEventKind.Respawn)
      case _         => None
    }
}
