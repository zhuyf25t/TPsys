package services.battle.microservices.projections.services

import services.mail.objects.MailRecord
import services.replay.objects.ReplayRecord

trait BattleMailPublisherPort {
  def publish(mail: MailRecord): Unit
}

trait BattleReplayWriterPort {
  def saveReplay(record: ReplayRecord): Unit
}
