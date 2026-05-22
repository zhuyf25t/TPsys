package system.services

import system.objects.{HealthResponse, HealthStatus}
import system.objects.{ServiceName, ServicePort}
import system.storage.StorageMode

trait HealthService {
  def current: HealthResponse
}

final class StaticHealthService(
  serviceName: ServiceName,
  port: ServicePort,
  storageMode: StorageMode
) extends HealthService {
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
