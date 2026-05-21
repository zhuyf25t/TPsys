package slaydemo.backend.shared.api

import io.circe.Encoder

import slaydemo.backend.shared.objects.{ServiceName, ServicePort}
import slaydemo.backend.shared.storage.StorageMode

enum HealthStatus {
  case Ok
}

enum HealthApiErrorCode {
  case MethodNotAllowed
}

object HealthRequestTarget {
  private val AllowedHealthPaths: Set[String] =
    Set("/health", "/api/health", "/api/healthapi")

  def isHealthPath(path: String): Boolean =
    AllowedHealthPaths.contains(path)
}

object HealthStatus {
  def wireValue(status: HealthStatus): String =
    status match {
      case HealthStatus.Ok => "ok"
    }
}

object HealthApiErrorCode {
  def wireValue(code: HealthApiErrorCode): String =
    code match {
      case HealthApiErrorCode.MethodNotAllowed => "method_not_allowed"
    }

  def message(code: HealthApiErrorCode): String =
    code match {
      case HealthApiErrorCode.MethodNotAllowed => "Method is not allowed."
    }

  def statusCode(code: HealthApiErrorCode): Int =
    code match {
      case HealthApiErrorCode.MethodNotAllowed => 405
    }
}

final case class HealthResponse(
  status: HealthStatus,
  service: ServiceName,
  port: ServicePort,
  storageMode: StorageMode
)

object HealthJsonCodec {
  given Encoder[HealthStatus] =
    Encoder.encodeString.contramap(HealthStatus.wireValue)

  given Encoder[ServiceName] =
    Encoder.encodeString.contramap(_.value)

  given Encoder[ServicePort] =
    Encoder.encodeInt.contramap(_.value)

  given Encoder[StorageMode] =
    Encoder.encodeString.contramap(StorageMode.wireValue)

  given Encoder[HealthResponse] =
    Encoder.forProduct4("status", "service", "port", "storageMode")(response =>
      (response.status, response.service, response.port, response.storageMode)
    )
}
