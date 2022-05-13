package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*

sealed trait Saga[F[_], A]:
  def map[B](f: A => B): Saga[F, B]
  def flatMap[B](f: A => Saga[F, B]): Saga[F, B]

extension [F[_], A](fa: F[A])

  def compensate(compensation: Either[Throwable, A] => F[Unit]): Saga[F, A] =
    Wrap(Step.Next(fa, compensation))

protected case class Wrap[F[_], A](step: Step[F, A]) extends Saga[F, A] {

  override def map[B](f: A => B): Saga[F, B] =
    flatMap(a => Wrap(Step.Pure(f(a))))

  override def flatMap[B](f: A => Saga[F, B]): Saga[F, B] =
    Wrap(Step.FlatMap(step, (a: A) => f(a).asInstanceOf[Wrap[F, B]].step))
}

object Saga:

  type TL[F[_]] = [A] =>> Saga[F, A]

  extension [F[_]: Stepper, A](saga: Saga[F, A])
    def run(): F[A] = summon[Stepper[F]].run(saga.asInstanceOf[Wrap[F, A]].step)

  given [F[_]]: Monad[TL[F]] with
    def pure[A](x: A): Saga[F, A]                                     = Wrap(Step.Pure(x))
    def flatMap[A, B](fa: Saga[F, A])(f: A => Saga[F, B]): Saga[F, B] = fa.flatMap(f)

    def tailRecM[A, B](a: A)(f: A => Saga[F, Either[A, B]]): Saga[F, B] = flatMap(f(a)) {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => pure(b)
    }

enum Step[F[_], A]:
  case Pure[F[_], A](value: A) extends Step[F, A]
  case Next[F[_], A](action: F[A], compensate: Either[Throwable, A] => F[Unit]) extends Step[F, A]
  case FlatMap[F[_], A, B](fa: Step[F, A], f: A => Step[F, B]) extends Step[F, B]

trait Stepper[F[_]]:
  def run[A](saga: Step[F, A]): F[A]

enum Direction[F[_], A]:
  case Forward(value: A, rollback: F[Unit]) extends Direction[F, A]
  case Rollback(value: Throwable, rollback: F[Unit]) extends Direction[F, A]

object Stepper:

  def default[F[_]: MonadThrow]: Stepper[F] = new Stepper[F] {
    val F = MonadThrow[F]

    override def run[A](saga: Step[F, A]): F[A] = {

      def go[X](step: Step[F, X]): F[Direction[F, X]] =
        step match
          case Step.Pure(value) => Direction.Forward(value, F.unit).pure[F]
          case Step.Next(action, compensate) =>
            action.attempt.flatMap { result =>
              result match
                case Left(e)  => Direction.Rollback(e, compensate(result)).pure[F]
                case Right(v) => Direction.Forward(v, compensate(result)).pure[F]
            }

          case Step.FlatMap(fa: Step[F, _], cont: (Any => Step[F, X])) =>
            go(fa).flatMap {
              case Direction.Forward(v, prevRollback: F[Unit]) =>
                go(cont(v)).attempt.flatMap {
                  case Right(Direction.Forward(r, rollback)) =>
                    Direction.Forward(r, rollback *> prevRollback).pure[F]
                  case Right(Direction.Rollback(e, rollback)) =>
                    Direction.Rollback(e, rollback *> prevRollback).pure[F]
                  case Left(e) =>
                    F.raiseError(e)
                }
              case err @ Direction.Rollback(_, _) =>
                err.asInstanceOf[Direction[F, X]].pure[F]
            }

      go(saga).flatMap {
        case Direction.Forward(a, _) => a.pure[F]
        case Direction.Rollback(e, rollback) =>
          rollback *> F.raiseError(e)
      }
    }
  }
