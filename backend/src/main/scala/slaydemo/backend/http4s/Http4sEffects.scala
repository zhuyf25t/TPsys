package slaydemo.backend.http4s

import cats.effect.IO

private[http4s] object Http4sEffects {
  def blocking[A](thunk: => A): IO[A] =
    IO.blocking(thunk)
}
