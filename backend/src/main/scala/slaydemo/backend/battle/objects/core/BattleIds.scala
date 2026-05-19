package slaydemo.backend.battle.objects.core

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
