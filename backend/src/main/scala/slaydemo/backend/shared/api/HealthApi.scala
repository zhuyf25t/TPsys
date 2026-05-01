package slaydemo.backend.shared.api

import slaydemo.backend.shared.objects.{ServiceName, ServicePort}
import slaydemo.backend.shared.storage.StorageMode

enum HealthStatus {
  case Ok
}

object HealthStatus {
  def wireValue(status: HealthStatus): String =
    status match {
      case HealthStatus.Ok => "ok"
    }
}

final case class HealthResponse(
  status: HealthStatus,
  service: ServiceName,
  port: ServicePort,
  storageMode: StorageMode
)
