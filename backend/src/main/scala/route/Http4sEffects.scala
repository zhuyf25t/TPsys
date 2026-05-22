package route

import cats.effect.IO

private[route] object Http4sEffects {
  def blocking[A](thunk: => A): IO[A] =
    IO.blocking(thunk)
}
