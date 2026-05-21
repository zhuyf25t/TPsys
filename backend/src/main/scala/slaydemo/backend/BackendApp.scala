package slaydemo.backend

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpServer
import slaydemo.backend.shared.database.PostgresSupport

object BackendApp {
  private[backend] val BaseRouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.BaseRouteContexts

  private[backend] val CompatibilityRouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.CompatibilityRouteContexts

  private[backend] val RouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.RouteContexts

  def main(args: Array[String]): Unit =
    start(BackendEnvironment.load())

  def start(env: Map[String, String] = BackendEnvironment.load()): Unit = {
    val runtime = BackendRuntime.fromEnvironment(env)
    Runtime.getRuntime.addShutdownHook(Thread(() => PostgresSupport.closeAll()))

    val server = HttpServer.create(InetSocketAddress(runtime.config.port.value), 0)
    BackendRouteRegistry.register(
      server,
      BackendRouteRegistry.routeHandlers(
        healthRoutes = runtime.healthRoutes,
        identityRoutes = runtime.identityRoutes,
        battleRoutes = runtime.battleRoutes,
        battleResultRoutes = runtime.battleResultRoutes,
        replayRoutes = runtime.replayRoutes,
        mailRoutes = runtime.mailRoutes,
        botProfileRoutes = runtime.botProfileRoutes,
        socialRoutes = runtime.socialRoutes,
        forumRoutes = runtime.forumRoutes,
        governanceRoutes = runtime.governanceRoutes
      )
    )
    server.setExecutor(Executors.newCachedThreadPool())
    server.start()

    println(s"Slay demo backend listening on http://127.0.0.1:${runtime.config.port.value}")
    awaitForever()
  }

  private def awaitForever(): Unit =
    while true do Thread.sleep(60_000L)
}
