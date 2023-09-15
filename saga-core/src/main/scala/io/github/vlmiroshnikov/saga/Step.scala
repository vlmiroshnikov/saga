package io.github.vlmiroshnikov.saga

enum Step[F[_], A]:
  case Pure(value: A) extends Step[F, A]
  case Next(action: F[A], compensate: Either[Throwable, A] => F[Unit]) extends Step[F, A]
  case FlatMap[F[_], A, B](fa: Step[F, A], f: A => Step[F, B]) extends Step[F, B]
