package scorex.lagonaki

import dispatch.{Http, url}
import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class APISpecification extends FunSuite with Matchers {

  import TestingCommons._

  test("Scorex API route") {
    val status = getRequest("/scorex/status")
    List("generating", "syncing") should contain((status \ "block_generator_status").as[String])
    List("synced", "syncing") should contain((status \ "history_synchronization_status").as[String])

    val version = getRequest("/scorex/version")
    (version \ "version").as[String].contains("Scorex") shouldBe true
    (version \ "version").as[String].contains("Lagonaki") shouldBe true
    (version \ "version").as[String].contains("v. 1.2.") shouldBe true
  }


  def getRequest(us: String, peer: String = peerUrl(application)): JsValue = {
    val request = Http(url(peer + us).GET)
    val response = Await.result(request, 10.seconds)
    Json.parse(response.getResponseBody)
  }
}