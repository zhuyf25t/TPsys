package slaydemo.backend.bots.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.bots.objects.*
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BotProfileRouteContractTest {
  def main(args: Array[String]): Unit = {
    getRendersProfilesAndCallsService()
    headDoesNotCallService()
    unsupportedMethodIsRejected()

    println("Bot profile route contract checks passed")
  }

  private def getRendersProfilesAndCallsService(): Unit = {
    val service = RecordingBotProfileService()
    service.profiles = Vector(profile())

    withBotProfileServer(service) { uri =>
      val response = get(uri.resolve("/bots/profiles"))

      assertEquals("profile list status", response.status, 200)
      assertContains("profile list wrapper", response.body, """"profiles":[""")
      assertContains("profile bot id", response.body, """"botId":"bot-route"""")
      assertContains("profile tone", response.body, """"profileTone":"scrappy"""")
      assertContains("profile skin", response.body, """"textureKey":"hero-route"""")
      assertEquals("profile list calls service", service.listCalls, 1)
    }
  }

  private def headDoesNotCallService(): Unit = {
    val service = RecordingBotProfileService()

    withBotProfileServer(service) { uri =>
      val response = head(uri.resolve("/bots/profiles"))

      assertEquals("head status", response.status, 200)
      assertEquals("head does not call service", service.listCalls, 0)
    }
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingBotProfileService()

    withBotProfileServer(service) { uri =>
      val response = postJson(uri.resolve("/bots/profiles"), "{}")

      assertEquals("unsupported method status", response.status, 405)
      assertContains("unsupported method code", response.body, """"code":"method_not_allowed"""")
      assertEquals("unsupported method does not call service", service.listCalls, 0)
    }
  }

  private def withBotProfileServer[A](service: RecordingBotProfileService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = BotProfileRoutes(service)
    server.createContext("/bots/profiles", exchange => routes.handle(exchange))
    server.start()
    try run(URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/"))
    finally server.stop(0)
  }

  private def get(uri: URI): RouteResponse = {
    val request = HttpRequest.newBuilder(uri).GET().build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    RouteResponse(response.statusCode(), response.body())
  }

  private def head(uri: URI): RouteResponse = {
    val request = HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    RouteResponse(response.statusCode(), response.body())
  }

  private def postJson(uri: URI, body: String): RouteResponse = {
    val request = HttpRequest
      .newBuilder(uri)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    RouteResponse(response.statusCode(), response.body())
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
