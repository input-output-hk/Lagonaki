package scorex.lagonaki

import org.scalatest.{FunSuite, Matchers}


class ScorexAPISpecification extends FunSuite with Matchers {

  import TestingCommons._

  test("/scorex/status API route") {
    val status = getRequest("/scorex/status")
    List("generating", "syncing") should contain((status \ "block_generator_status").as[String])
    List("synced", "syncing") should contain((status \ "history_synchronization_status").as[String])
  }

  test("/scorex/version API route") {
    val version = getRequest("/scorex/version")
    (version \ "version").as[String].contains("Scorex") shouldBe true
    (version \ "version").as[String].contains("Lagonaki") shouldBe true
    (version \ "version").as[String].contains("v. 1.2.") shouldBe true
  }
}