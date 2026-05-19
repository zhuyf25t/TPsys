package slaydemo.backend.shared.api

final class BackendIO[+A] private (private val thunk: () => A) {
  def map[B](transform: A => B): BackendIO[B] =
    BackendIO.delay(transform(unsafeRun()))

  def flatMap[B](transform: A => BackendIO[B]): BackendIO[B] =
    BackendIO.delay(transform(unsafeRun()).unsafeRun())

  def unsafeRun(): A =
    thunk()
}

object BackendIO {
  def delay[A](value: => A): BackendIO[A] =
    new BackendIO(() => value)

  def pure[A](value: A): BackendIO[A] =
    new BackendIO(() => value)
}
