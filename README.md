# Simple implementation the SAGA pattern


## Example

```
import cats.*
import cats.syntax.all.*
import cats.effect.*
import cats.effect.syntax.all.*
import io.github.vlmiroshnikov.saga.*

object SimpleApp extends IOApp.Simple {
  given Stepper[IO] = Stepper.default[IO]

  def run: IO[Unit] = {

    def step(n: Int): Saga[IO, Unit] =
      IO.println(s"Step-$n").compensate(_ => IO.println(s"rollback Step $n"))

    val saga = for
      _ <- step(1)
      _ <- step(2)
    yield ()

    saga.run().flatMap {
      case Left(e)  => IO.println("Failed and rollback")
      case Rigth(_) => IO.println("Completed")
    }
  }
```


## Dependencies
* scala 3.*
* cats, cats-effects

 
