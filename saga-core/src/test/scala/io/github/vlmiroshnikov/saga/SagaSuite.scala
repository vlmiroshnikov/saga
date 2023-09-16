package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*
import cats.effect.*
import io.github.vlmiroshnikov.saga.*
import munit.*

class SagaSuite extends munit.CatsEffectSuite {
  case object FailedException extends RuntimeException

  given Stepper[IO] = Stepper.default[IO]

  val counter = ResourceFunFixture(Resource.eval(Ref.of[IO, Int](0)))

  def step(n: Int, failed: Boolean = false)(using counter: Ref[IO, Int]): Saga[IO, Unit] =
    val step = for
      _ <- IO.println(s"Step-$n")
      _ <- counter.update(v => v + 1)
      _ <- IO.raiseError(FailedException).whenA(failed)
    yield ()
    step.compensate(_ => IO.println(s"Rollback Step-$n") *> counter.update(v => v - 1))

  counter.test("all succeed") { cnt => 
    given Ref[IO, Int] = cnt

    val r = step(1).flatMap(_ => step(2)).run
    assertIO(cnt.get, 2)
    assertIO(r, ().asRight)
  }

  counter.test("failed and rollback step 1") { cnt =>
    given Ref[IO, Int] = cnt

    val r = step(1, true).flatMap(_ => step(2)).run
    assertIO(r, Left(FailedException))
    assertIO(cnt.get, 0)
  }

  counter.test("failed and rollback step 2") { cnt =>
    given Ref[IO, Int] = cnt

    val r = step(1).flatMap(_ => step(2, true)).run  

    assertIO(r, Left(FailedException))
    assertIO(cnt.get, 0)
  }
}
