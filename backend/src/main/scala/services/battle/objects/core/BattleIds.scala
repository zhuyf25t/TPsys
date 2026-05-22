package services.battle.objects.core

import services.battle.objects.*
import services.battle.objects.core.*
import services.battle.objects.event.*
import services.battle.objects.pickup.*
import services.battle.objects.player.*
import services.battle.objects.projectile.*
import services.battle.objects.queue.*
import services.battle.objects.replay.*
import services.battle.objects.result.*
import services.battle.objects.skill.*
import services.battle.objects.weapon.*

final case class TicketId(value: String) extends AnyVal
final case class QueueRequestId(value: String) extends AnyVal
final case class RoomId(value: String) extends AnyVal
final case class BattleId(value: String) extends AnyVal
final case class PlayerId(value: String) extends AnyVal
final case class HeroId(value: String) extends AnyVal
final case class ProjectileId(value: String) extends AnyVal
final case class SlowFieldId(value: String) extends AnyVal
final case class PickupId(value: String) extends AnyVal
final case class BattleEventId(value: String) extends AnyVal
final case class BattleResultId(value: String) extends AnyVal
