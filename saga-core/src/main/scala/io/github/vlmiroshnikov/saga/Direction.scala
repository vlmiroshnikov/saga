package io.github.vlmiroshnikov.saga

enum Direction[F[_], A]:
  case Forward(value: A, rollback: F[Unit]) extends Direction[F, A]
  case Rollback(value: Throwable, rollback: F[Unit]) extends Direction[F, A]
