package slaydemo.backend.bots.services

import slaydemo.backend.bots.api.BotProfileView

trait BotProfileService {
  def list(): Seq[BotProfileView]
}
