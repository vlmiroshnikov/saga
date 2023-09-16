package io.github.vlmiroshnikov.saga

import cats.*
import cats.syntax.all.*

trait Stepper[F[_]]:
  def runStep[A](saga: Step[F, A]): F[Either[Throwable, A]]

object Stepper:

  def default[F[_]](using F: MonadThrow[F]): Stepper[F] = new Stepper[F] {

    override def runStep[A](saga: Step[F, A]): F[Either[Throwable, A]] = {

      def go[X](step: Step[F, X]): F[Direction[F, X]] =
        step match
          case Step.Pure(value) => Direction.Forward(value, F.unit).pure[F]
          case Step.Next(action, compensate) =>
            action.attempt.map {
              case result @ Left(e)  => Direction.Rollback(e, compensate(result))
              case result @ Right(v) => Direction.Forward(v, compensate(result))
            }

          case Step.FlatMap(fa, cont) =>
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
        case Direction.Forward(a, _)         => a.asRight.pure[F]
        case Direction.Rollback(e, rollback) => rollback.as(Left(e))
      }
    }
  }
