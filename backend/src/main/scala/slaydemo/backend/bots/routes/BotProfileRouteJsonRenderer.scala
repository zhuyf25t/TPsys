package slaydemo.backend.bots.routes

import slaydemo.backend.bots.objects.{BotProfileRecord, BotProfileTone}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object BotProfileRouteJsonRenderer {
  def renderProfiles(profiles: Vector[BotProfileRecord]): String =
    renderObject(Vector("profiles" -> profiles.map(renderProfile).mkString("[", ",", "]")))

  private def renderProfile(profile: BotProfileRecord): String =
    renderObject(
      Vector(
        "botId" -> jsonString(profile.botId.value),
        "handle" -> jsonString(profile.handle.value),
        "displayName" -> jsonString(profile.displayName.value),
        "initialRating" -> profile.initialRating.value.toString,
        "profileTone" -> jsonString(BotProfileTone.wireValue(profile.profileTone)),
        "strategyLabel" -> jsonString(profile.strategyLabel.value),
        "skin" -> renderSkin(profile)
      )
    )

  private def renderSkin(profile: BotProfileRecord): String =
    renderObject(
      Vector(
        "avatarKey" -> jsonString(profile.skin.avatarKey.value),
        "textureKey" -> jsonString(profile.skin.textureKey.value),
        "label" -> jsonString(profile.skin.label.value)
      )
    )

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
