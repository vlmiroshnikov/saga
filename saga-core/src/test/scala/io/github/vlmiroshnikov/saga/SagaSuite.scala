package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*
import cats.effect.*
import cats.effect.syntax.all.*
import io.github.vlmiroshnikov.saga.*

class SagaSuite extends munit.CatsEffectSuite {

  test("build saga") {
    given Stepper[IO] = Stepper.default[IO]

    val step1 = IO.println("Step-1")
    val step2 = IO.println("Step-2").as(2)

    val saga = for
      _      <- step1.compensate(e => IO.println("Rollback Step1"))
      second <- step2.compensate(e => IO.println("Rollback Step2"))
    yield second

    for
      r <- saga.run()
      _ <- IO.println(r)
    yield assertEquals(r, 2)
  }
}
