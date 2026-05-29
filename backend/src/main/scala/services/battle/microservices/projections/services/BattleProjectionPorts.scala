package services.battle.microservices.projections.services

import cats.effect.IO

import services.mail.objects.MailRecord
import services.replay.objects.ReplayRecord

trait BattleMailPublisherPort {
  def publish(mail: MailRecord): IO[Unit]
}

trait BattleReplayWriterPort {
  def saveReplay(record: ReplayRecord): IO[Unit]
}
