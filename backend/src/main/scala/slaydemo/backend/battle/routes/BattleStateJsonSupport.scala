package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.*
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object BattleStateJsonSupport {
  def renderVector(vector: BattleVector2): String =
    renderObject(Vector("x" -> vector.x.toString, "y" -> vector.y.toString))

  def renderOptionalAmmo(value: Option[AmmoCount]): String =
    value.map(_.value.toString).getOrElse("null")

  def renderOptionalElapsed(value: Option[ElapsedMillis]): String =
    value.map(_.value.toString).getOrElse("null")

  def renderOptionalHitPoints(value: Option[HitPoints]): String =
    value.map(_.value.toString).getOrElse("null")

  def renderOptionalDamage(value: Option[Damage]): String =
    value.map(_.value.toString).getOrElse("null")

  def renderOptionalString(value: Option[String]): String =
    value.filter(_.trim.nonEmpty).map(jsonString).getOrElse("null")

  def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
