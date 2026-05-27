package system.api

import cats.effect.IO
import io.circe.syntax.*
import system.services.HealthService

object HealthAPIMessage {
  def registered(service: HealthService): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = APIMessage.apiNameFromClassName(getClass.getSimpleName),
      requiresUserToken = false,
      planJson = (_, _) => IO.blocking(service.current).map(_.asJson)
    )
}
