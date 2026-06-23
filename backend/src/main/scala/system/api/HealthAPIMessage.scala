package system.api

import cats.effect.IO
import io.circe.Decoder
import system.objects.HealthResponse
import system.objects.HealthJsonCodec.given
import system.services.HealthService

import java.sql.Connection

final case class HealthAPIMessage() extends APIMessageWithContext[HealthService, HealthResponse] {
  override def plan(service: HealthService, connection: Connection): IO[HealthResponse] =
    IO.blocking(service.current)
}

object HealthAPIMessage {
  given Decoder[HealthAPIMessage] =
    Decoder.const(HealthAPIMessage())

  def registered(service: HealthService): RegisteredAPIMessage =
    RegisteredAPIMessage.apiWithContext[HealthService, HealthAPIMessage, HealthResponse](service)
}
