package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*
import cats.effect.*
import cats.effect.syntax.all.*
import io.github.vlmiroshnikov.saga.*

class SagaSuite extends munit.CatsEffectSuite {
  case class FailedException() extends RuntimeException

  given Stepper[IO] = Stepper.default[IO]

  def step(n: Int, failed: Boolean = false)(counter: Ref[IO, Int]): Saga[IO, Unit] =
    val step = for
      _ <- IO.println(s"Step-$n")
      _ <- counter.update(v => v + 1)
      _ <- IO.raiseError(FailedException()).whenA(failed)
    yield ()
    step.compensate(_ => IO.println(s"Rollback Step-$n") *> counter.update(v => v - 1))

  test("all succeed") {
    for
      counter <- Ref.of[IO, Int](0)
      _       <- step(1)(counter).flatMap(_ => step(2)(counter)).run()
      r       <- counter.get
    yield assertEquals(r, 2)
  }

  test("failed and rollback step 1") {
    for
      counter <- Ref.of[IO, Int](0)
      r       <- step(1, true)(counter).flatMap(_ => step(2)(counter)).run()
      c       <- counter.get
    yield {
      assertEquals(r, Left(FailedException()))
      assertEquals(c, 0)
    }
  }

  test("failed and rollback step 2") {
    for
      counter <- Ref.of[IO, Int](0)
      r       <- step(1)(counter).flatMap(_ => step(2, true)(counter)).run()
      c       <- counter.get
    yield {
      assertEquals(r, Left(FailedException()))
      assertEquals(c, 0)
    }
  }
}
