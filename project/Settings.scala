import sbt.Keys.*
import sbt.*

object Versions {
  val dotty      = "3.3.1"
  val cats       = "2.10.0"
  val catsEffect = "3.5.1"
  val munit      = "1.0.0-M10"
}

object Settings {

  lazy val settings = Seq(
    scalacOptions ++= Seq("-new-syntax", "-rewrite", "-indent")
  )

  lazy val cats        = Seq("org.typelevel" %% "cats-core").map(_ % Versions.cats)
  lazy val catsEffect  = Seq("org.typelevel" %% "cats-effect").map(_ % Versions.catsEffect)
  lazy val munit       = Seq("org.scalameta" %% "munit" % Versions.munit % Test)
  lazy val munitEffect = Seq("org.typelevel" %% "munit-cats-effect" % "2.0.0-M3" % "test")
}
