package slaydemo.backend.replay.planners

import slaydemo.backend.replay.api.ReplayCatalogView

trait ReplayPlanner {
  def buildReplayCatalog(): Vector[ReplayCatalogView]
}
