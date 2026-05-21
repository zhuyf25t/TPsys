package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Method, Request}

import slaydemo.backend.bots.objects.*
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BotProfileHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    getRendersProfilesAndCallsService()
    headDoesNotCallService()
    unsupportedMethodIsRejected()

    println("Bot profile http4s contract checks passed")
  }

  private def getRendersProfilesAndCallsService(): Unit = {
    val service = RecordingBotProfileService()
    service.profiles = Vector(profile())

    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/bots/profiles"))

    assertEquals("profile list status", response.status, 200)
    assertContains("profile list wrapper", response.body, """"profiles":[""")
    assertContains("profile bot id", response.body, """"botId":"bot-route"""")
    assertContains("profile tone", response.body, """"profileTone":"scrappy"""")
    assertContains("profile skin", response.body, """"textureKey":"hero-route"""")
    assertEquals("profile list calls service", service.listCalls, 1)
  }

  private def headDoesNotCallService(): Unit = {
    val service = RecordingBotProfileService()

    val response = run(service, Request[IO](method = Method.HEAD, uri = uri"/bot/profiles"))

    assertEquals("head status", response.status, 200)
    assertEquals("head does not call service", service.listCalls, 0)
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingBotProfileService()

    val response = run(service, Request[IO](method = Method.POST, uri = uri"/bots/profiles").withEntity("{}"))

    assertEquals("unsupported method status", response.status, 405)
    assertContains("unsupported method code", response.body, """"code":"method_not_allowed"""")
    assertEquals("unsupported method does not call service", service.listCalls, 0)
  }

  private def run(service: BotProfileService, request: Request[IO]): RouteResponse = {
    val response = BotProfileHttp4sRoutes
      .routes(service)
      .orNotFound
      .run(request)
      .unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingBotProfileService extends BotProfileService {
    var profiles: Vector[BotProfileRecord] = Vector.empty
    var listCalls: Int = 0

    override def list(): Vector[BotProfileRecord] = {
      listCalls += 1
      profiles
    }
  }

  private def profile(): BotProfileRecord =
    BotProfileRecord(
      botId = BotId("bot-route"),
      handle = PlayerHandle("cpu-route"),
      displayName = DisplayName("Route Bot"),
      initialRating = BotInitialRating(1_010),
      profileTone = BotProfileTone.Scrappy,
      strategyLabel = BotStrategyLabel("Route strategy"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("route-avatar"),
        textureKey = BotTextureKey("hero-route"),
        label = BotSkinLabel("Route skin")
      ),
      profileOrder = BotProfileOrder(0)
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
