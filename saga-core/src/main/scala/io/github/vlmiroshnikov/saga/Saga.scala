package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*

sealed trait Saga[F[_], A](private val step: Step[F, A]):
  def map[B](f: A => B): Saga[F, B]
  def flatMap[B](f: A => Saga[F, B]): Saga[F, B]

extension [F[_], A](fa: F[A])

  def compensate(compensation: Either[Throwable, A] => F[Unit]): Saga[F, A] =
    Saga.Wrap(Step.Next(fa, compensation))

object Saga:

  extension [F[_], A](saga: Saga[F, A])

    def run(using stepper: Stepper[F]): F[Either[Throwable, A]] =
      stepper.runStep(saga.step)

  given [F[_]]: Monad[[A] =>> Saga[F, A]] with

    def pure[A](x: A): Saga[F, A] =
      Wrap(Step.Pure(x))

    def flatMap[A, B](fa: Saga[F, A])(f: A => Saga[F, B]): Saga[F, B] =
      fa.flatMap(f)

    def tailRecM[A, B](a: A)(f: A => Saga[F, Either[A, B]]): Saga[F, B] =
      flatMap(f(a)) {
        case Left(aa) => tailRecM(aa)(f)
        case Right(b) => pure(b)
      }

  private[saga] case class Wrap[F[_], A](step: Step[F, A]) extends Saga[F, A](step):

    override def map[B](f: A => B): Saga[F, B] =
      flatMap(a => Wrap(Step.Pure(f(a))))

    override def flatMap[B](f: A => Saga[F, B]): Saga[F, B] =
      Wrap(Step.FlatMap(step, (a: A) => f(a).step))

end Saga
