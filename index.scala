//> using platform "js"
//> using scala "3.2.2"
//> using jsVersion "1.13.1"
//> using toolkit "typelevel:0.0.10"
//> using jsModuleKind "common"

import cats.Order.catsKernelOrderingForOrder
import cats.data.*
import cats.effect.*
import cats.effect.std.*
import cats.syntax.all.*
import fs2.*
import fs2.io.file.*
import fs2.io.process.*

object index extends IOApp.Simple:
  def getInput(input: String) =
    Env[IO].get(s"INPUT_${input.toUpperCase}")

  def getPath = getInput("path").map(_.foldMap(Path(_)))

  def getMigrations(path: Path) =
    Files[IO].list(path).compile.toList.map(_.sorted)

  def getAdditions(path: Path) =
    Stream
      .resource(
        ProcessBuilder(
          "git",
          "diff-tree",
          "--no-commit-id",
          "--name-status",
          "-m",
          "-r",
          "HEAD",
          path.toString
        ).spawn[IO]
      )
      .flatMap(_.stdout)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filterNot(_.isEmpty)
      .map(_.split("\\s+").toList)
      .evalMap {
        case List(status, path) if status == "A" => IO.pure(Path(path))
        case l => IO.raiseError(new RuntimeException(s"Invalid diff: $l"))
      }
      .compile
      .toList
      .map(_.sorted)

  def run = for
    path <- getPath
    migrations <- getMigrations(path)
    additions <- getAdditions(path)
    _ <- IO.raiseWhen(!migrations.endsWith(additions)) {
      new RuntimeException(
        s"Added migrations are not strictly increasing:\n${additions.mkString("\n")}"
      )
    }
    _ <-
      if additions.nonEmpty then
        IO.println(
          s"Added migrations are valid:\n${additions.map("- " + _).mkString("\n")}"
        )
      else IO.println("No changes to migrations")
  yield ()
