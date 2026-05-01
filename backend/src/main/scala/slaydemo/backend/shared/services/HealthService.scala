package slaydemo.backend.shared.services

import slaydemo.backend.shared.api.{HealthResponse, HealthStatus}
import slaydemo.backend.shared.objects.{ServiceName, ServicePort}
import slaydemo.backend.shared.storage.StorageMode

trait HealthService {
  def current: HealthResponse
}

final class StaticHealthService(serviceName: ServiceName, port: ServicePort, storageMode: StorageMode) extends HealthService {
  override def current: HealthResponse =
    HealthResponse(
      status = HealthStatus.Ok,
      service = serviceName,
      port = port,
      storageMode = storageMode
    )
}

object StaticHealthService {
  def apply(serviceName: ServiceName, port: ServicePort, storageMode: StorageMode): StaticHealthService =
    new StaticHealthService(serviceName, port, storageMode)
}
