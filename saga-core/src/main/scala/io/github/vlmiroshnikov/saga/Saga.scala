package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*

sealed trait Saga[F[_], A]:
  def map[B](f: A => B): Saga[F, B]

  def flatMap[B](f: A => Saga[F, B]): Saga[F, B]

extension [F[_], A](fa: F[A])

  def compensate(compensation: Either[Throwable, A] => F[Unit]): Saga[F, A] =
    Wrap(Step.Next(fa, compensation))

object Saga:

  type TL[F[_]] = [A] =>> Saga[F, A]

  extension [F[_]: Stepper, A](saga: Saga[F, A])
    def run(): F[A] = summon[Stepper[F]].run(saga.asInstanceOf[Wrap[F, A]].step)

  given [F[_]]: Monad[TL[F]] with

    def pure[A](x: A): Saga[F, A] =
      Wrap(Step.Pure(x))

    def flatMap[A, B](fa: Saga[F, A])(f: A => Saga[F, B]): Saga[F, B] =
      fa.flatMap(f)

    def tailRecM[A, B](a: A)(f: A => Saga[F, Either[A, B]]): Saga[F, B] = flatMap(f(a)) {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => pure(b)
    }

protected case class Wrap[F[_], A](step: Step[F, A]) extends Saga[F, A] {

  override def map[B](f: A => B): Saga[F, B] =
    flatMap(a => Wrap(Step.Pure(f(a))))

  override def flatMap[B](f: A => Saga[F, B]): Saga[F, B] =
    Wrap(Step.FlatMap(step, (a: A) => f(a).asInstanceOf[Wrap[F, B]].step))
}
