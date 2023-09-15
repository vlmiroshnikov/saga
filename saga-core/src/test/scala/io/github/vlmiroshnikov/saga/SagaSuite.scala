package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*
import cats.effect.*
import cats.effect.syntax.all.*
import io.github.vlmiroshnikov.saga.*
import munit.*

class SagaSuite extends munit.CatsEffectSuite {
  case object FailedException extends RuntimeException

  given Stepper[IO] = Stepper.default[IO]

  def step(n: Int, failed: Boolean = false)(using counter: Ref[IO, Int]): Saga[IO, Unit] =
    val step = for
      _ <- IO.println(s"Step-$n")
      _ <- counter.update(v => v + 1)
      _ <- IO.raiseError(FailedException).whenA(failed)
    yield ()
    step.compensate(_ => IO.println(s"Rollback Step-$n") *> counter.update(v => v - 1))

  test("all succeed") {
    for
      given Ref[IO, Int] <- Ref.of[IO, Int](0)
      _       <- step(1).flatMap(_ => step(2)).run()
      c       <- summon[Ref[IO, Int]].get
    yield assertEquals(c, 2)
  }

  test("failed and rollback step 1") {
    for
      given Ref[IO, Int] <- Ref.of[IO, Int](0)
      r       <- step(1, true).flatMap(_ => step(2)).run()
      c       <- summon[Ref[IO, Int]].get
    yield 
      assertEquals(r, Left(FailedException))
      assertEquals(c, 0)
    
  }

  test("failed and rollback step 2") {
    for
      given Ref[IO, Int] <- Ref.of[IO, Int](0)
      r       <- step(1).flatMap(_ => step(2, true)).run()
      c       <- summon[Ref[IO, Int]].get
    yield 
      assertEquals(r, Left(FailedException))
      assertEquals(c, 0)
    
  }
}
