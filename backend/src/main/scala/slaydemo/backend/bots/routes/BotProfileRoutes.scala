package slaydemo.backend.bots.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.bots.objects.{BotProfileRecord, BotProfileTone}
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.shared.routes.HttpRouteSupport

final class BotProfileRoutes(service: BotProfileService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" =>
          HttpRouteSupport.sendJson(exchange, 200, renderProfiles(service.list()))
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def renderProfiles(profiles: Vector[BotProfileRecord]): String =
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

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")
}

object BotProfileRoutes {
  def apply(service: BotProfileService): BotProfileRoutes =
    new BotProfileRoutes(service)
}
