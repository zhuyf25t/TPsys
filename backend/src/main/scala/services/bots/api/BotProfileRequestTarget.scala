package services.bots.api

object BotProfileRequestTarget {
  private val AllowedProfilePaths: Set[String] =
    Set("/bots/profiles", "/bot/profiles", "/api/bots/profiles", "/api/bot/profiles")

  def isProfilePath(path: String): Boolean =
    AllowedProfilePaths.contains(path)
}
