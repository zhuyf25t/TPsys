package slaydemo.backend.shared.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.shared.api.{HealthResponse, HealthStatus}
import slaydemo.backend.shared.objects.{ServiceName, ServicePort}
import slaydemo.backend.shared.services.HealthService
import slaydemo.backend.shared.storage.StorageMode

object HealthRouteContractTest {
  def main(args: Array[String]): Unit = {
    getRendersHealthAndCallsService()
    headDoesNotCallService()
    unsupportedMethodIsRejected()

    println("Health route contract checks passed")
  }

  private def getRendersHealthAndCallsService(): Unit = {
    val service = RecordingHealthService()

    withHealthServer(service) { uri =>
      val response = get(uri.resolve("/health"))

      assertEquals("health status", response.status, 200)
      assertContains("health ok", response.body, """"status":"ok"""")
      assertContains("health service", response.body, """"service":"route-health"""")
      assertContains("health port", response.body, """"port":18080""")
      assertContains("health storage mode", response.body, """"storageMode":"postgres"""")
      assertEquals("health calls service", service.currentCalls, 1)
    }
  }

  private def headDoesNotCallService(): Unit = {
    val service = RecordingHealthService()

    withHealthServer(service) { uri =>
      val response = head(uri.resolve("/health"))

      assertEquals("head status", response.status, 200)
      assertEquals("head does not call service", service.currentCalls, 0)
    }
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingHealthService()

    withHealthServer(service) { uri =>
      val response = postJson(uri.resolve("/health"), "{}")

      assertEquals("unsupported method status", response.status, 405)
      assertEquals("unsupported method body", response.body, """{"error":"method_not_allowed"}""")
      assertEquals("unsupported method does not call service", service.currentCalls, 0)
    }
  }

  private def withHealthServer[A](service: RecordingHealthService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = HealthRoutes(service)
    server.createContext("/health", exchange => routes.handle(exchange))
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

  private final class RecordingHealthService extends HealthService {
    var currentCalls: Int = 0

    override def current: HealthResponse = {
      currentCalls += 1
      HealthResponse(
        status = HealthStatus.Ok,
        service = ServiceName("route-health"),
        port = ServicePort.unsafe(18_080),
        storageMode = StorageMode.Postgres
      )
    }
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
