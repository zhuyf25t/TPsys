package services

import system.objects.ServicePort
import system.storage.{StorageConfig, StorageConfigError}

final case class BackendConfig(
  port: ServicePort,
  storage: StorageConfig
)

enum BackendConfigError {
  case InvalidStorage(error: StorageConfigError)

  def message: String =
    this match {
      case BackendConfigError.InvalidStorage(error) => error.message
    }
}

object BackendConfig {
  val DefaultPort: ServicePort = ServicePort.unsafe(8080)

  def fromEnvironment(env: Map[String, String]): Either[BackendConfigError, BackendConfig] = {
    val configuredPort = env
      .get("SLAY_DEMO_BACKEND_PORT")
      .flatMap(ServicePort.fromString)
      .getOrElse(DefaultPort)

    StorageConfig
      .fromEnvironment(env)
      .left
      .map(BackendConfigError.InvalidStorage.apply)
      .map(storage => BackendConfig(port = configuredPort, storage = storage))
  }

  def unsafeFromEnvironment(env: Map[String, String]): BackendConfig =
    fromEnvironment(env).fold(
      error => throw IllegalArgumentException(error.message),
      config => config
    )
}
