package slaydemo.backend.shared.objects

import scala.util.Try

final case class ServiceName(value: String) extends AnyVal

object ServiceName {
  val Backend: ServiceName = ServiceName("slay-demo-backend")
}

final case class ServicePort(value: Int) extends AnyVal

object ServicePort {
  private val MinPort = 1
  private val MaxPort = 65535

  def fromInt(value: Int): Option[ServicePort] =
    Option.when(value >= MinPort && value <= MaxPort)(ServicePort(value))

  def fromString(value: String): Option[ServicePort] =
    Try(value.trim.toInt).toOption.flatMap(fromInt)

  def unsafe(value: Int): ServicePort =
    fromInt(value).getOrElse {
      throw IllegalArgumentException(s"Invalid service port: $value")
    }
}
