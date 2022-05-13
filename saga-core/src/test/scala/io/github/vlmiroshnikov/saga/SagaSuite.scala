package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*
import cats.effect.*
import cats.effect.syntax.all.*
import io.github.vlmiroshnikov.saga.*

class SagaSuite extends munit.CatsEffectSuite {

  test("build saga") {
    given Stepper[IO] = Stepper.default[IO]

    val step1 = IO.println("Step-1").as(1)
    val step2 = IO.println("Step-2") *> IO.raiseError(new Exception("errr"))

    val saga = for
      first  <- step1.compensate(e => IO.println("Rollback Step1") *> IO.raiseError(new Exception("rollback error")))
      second <- step2.compensate(e => IO.println("Rollback Step2"))
    yield second

    for
      r <- saga.run()
      _ <- IO.println(r)
    yield ()
  }
}
