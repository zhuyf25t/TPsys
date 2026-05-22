package system.objects

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

import system.objects.{ServiceName, ServicePort}
import system.storage.StorageMode

enum HealthStatus {
  case Ok
}

enum HealthApiErrorCode {
  case MethodNotAllowed
}

object HealthApiErrorCode {
  def wireValue(code: HealthApiErrorCode): String =
    code match {
      case HealthApiErrorCode.MethodNotAllowed => "method_not_allowed"
    }

  def message(code: HealthApiErrorCode): String =
    code match {
      case HealthApiErrorCode.MethodNotAllowed => "Only GET and OPTIONS are supported."
    }

  def statusCode(code: HealthApiErrorCode): Int =
    code match {
      case HealthApiErrorCode.MethodNotAllowed => 405
    }
}

object HealthRequestTarget {
  private val AllowedHealthPaths: Set[String] =
    Set("/health", "/api/health")

  def isHealthPath(path: String): Boolean =
    AllowedHealthPaths.contains(path)
}

object HealthStatus {
  def wireValue(status: HealthStatus): String =
    status match {
      case HealthStatus.Ok => "ok"
    }

  def fromWire(value: String): Option[HealthStatus] =
    value match {
      case "ok" => Some(HealthStatus.Ok)
      case _    => None
    }

  given Encoder[HealthStatus] =
    Encoder.encodeString.contramap(wireValue)

  given Decoder[HealthStatus] =
    Decoder.decodeString.emap(value => fromWire(value).toRight(s"Invalid health status: $value"))
}

final case class HealthResponse(
  status: HealthStatus,
  service: ServiceName,
  port: ServicePort,
  storageMode: StorageMode
)

object HealthResponse {
  given Encoder[HealthResponse] =
    Encoder.forProduct4("status", "service", "port", "storageMode")(response =>
      (
        HealthStatus.wireValue(response.status),
        response.service.value,
        response.port.value,
        StorageMode.wireValue(response.storageMode)
      )
    )

  given Decoder[HealthResponse] = (cursor: HCursor) =>
    for
      statusText <- cursor.downField("status").as[String]
      status <- HealthStatus.fromWire(statusText).toRight(DecodingFailure(s"Invalid health status: $statusText", cursor.history))
      service <- cursor.downField("service").as[String].map(ServiceName.apply)
      portValue <- cursor.downField("port").as[Int]
      port <- ServicePort.fromInt(portValue).toRight(DecodingFailure(s"Invalid service port: $portValue", cursor.history))
      storageModeText <- cursor.downField("storageMode").as[String]
      storageMode <- StorageMode.fromEnvironmentValue(storageModeText).left.map(error => DecodingFailure(error.message, cursor.history))
    yield HealthResponse(status, service, port, storageMode)
}

object HealthJsonCodec {
  export HealthResponse.given
}
