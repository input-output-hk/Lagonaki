package scorex.lagonaki

import org.scalatest.{FunSuite, Matchers}
import scorex.crypto.encode.Base58


class ConsensusAPISpecification extends FunSuite with Matchers {

  import TestingCommons._

  val genesis = application.blockStorage.history.genesis
  val genesisId = genesis.encodedId

  test("/consensus/algo") {
    val response = getRequest("/consensus/algo")
    (response \ "consensusAlgo").as[String] shouldBe "perma"
  }

  test("/consensus/target") {
    val response = getRequest("/consensus/target")
    (response \ "target").as[String] shouldBe "7998056171325776434104437152907813376728872407881581793920540837651058889700"
  }

  test("/consensus/target/{blockId}") {
    val response = getRequest(s"/consensus/target/$genesisId")
    (response \ "target").as[String] shouldBe "7998056171325776434104437152907813376728872407881581793920540837651058889700"
  }

  test("/consensus/puz/{blockId}") {
    val response = getRequest(s"/consensus/puz/$genesisId")
    (response \ "puz").as[String] shouldBe "11111111111111111111111111111111"
  }

  test("/consensus/puz") {
    val puz = application.consensusModule.generatePuz(genesis)
    val response = getRequest(s"/consensus/puz")
    (response \ "puz").as[String] shouldBe Base58.encode(puz)
  }

}