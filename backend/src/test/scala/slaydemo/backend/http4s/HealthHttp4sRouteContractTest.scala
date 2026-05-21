package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Method, Request}
import org.http4s.implicits.uri

import slaydemo.backend.shared.api.{HealthResponse, HealthStatus}
import slaydemo.backend.shared.objects.{ServiceName, ServicePort}
import slaydemo.backend.shared.services.HealthService
import slaydemo.backend.shared.storage.StorageMode

object HealthHttp4sRouteContractTest {
  def main(args: Array[String]): Unit = {
    getRendersLegacyAndApiHealthPaths()
    headDoesNotCallService()
    optionsIsCorsPreflight()
    unsupportedMethodIsRejected()

    println("Health http4s route contract checks passed")
  }

  private def getRendersLegacyAndApiHealthPaths(): Unit =
    Vector("/health", "/api/health", "/api/healthapi").foreach { path =>
      val service = RecordingHealthService()
      val response = run(service, Request[IO](method = Method.GET, uri = uriFrom(path)))

      assertEquals(s"$path status", response.status, 200)
      assertContains(s"$path health ok", response.body, """"status":"ok"""")
      assertContains(s"$path service", response.body, """"service":"route-health"""")
      assertContains(s"$path port", response.body, """"port":18080""")
      assertContains(s"$path storage mode", response.body, """"storageMode":"postgres"""")
      assertEquals(s"$path calls service", service.currentCalls, 1)
    }

  private def headDoesNotCallService(): Unit = {
    val service = RecordingHealthService()
    val response = run(service, Request[IO](method = Method.HEAD, uri = uri"/health"))

    assertEquals("head status", response.status, 200)
    assertEquals("head body", response.body, "")
    assertEquals("head does not call service", service.currentCalls, 0)
  }

  private def optionsIsCorsPreflight(): Unit = {
    val service = RecordingHealthService()
    val response = run(service, Request[IO](method = Method.OPTIONS, uri = uri"/api/healthapi"))

    assertEquals("options status", response.status, 204)
    assertEquals("options does not call service", service.currentCalls, 0)
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingHealthService()
    val response = run(service, Request[IO](method = Method.POST, uri = uri"/health").withEntity("{}"))

    assertEquals("unsupported method status", response.status, 405)
    assertEquals("unsupported method body", response.body, """{"error":"method_not_allowed"}""")
    assertEquals("unsupported method does not call service", service.currentCalls, 0)
  }

  private def run(service: RecordingHealthService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.healthRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private def uriFrom(path: String): org.http4s.Uri =
    org.http4s.Uri.unsafeFromString(path)

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
