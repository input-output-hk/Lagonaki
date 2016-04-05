package scorex.lagonaki

import java.io.{RandomAccessFile, File}
import java.nio.file.{Paths, Files}

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import scorex.account.Account
import scorex.api.http._
import scorex.app.ApplicationVersion
import scorex.crypto.ads.merkle.{MerkleTree, AuthDataBlock}
import scorex.crypto.hash.FastCryptographicHash
import scorex.lagonaki.http.{ScorexApiRoute, DebugApiRoute}
import scorex.lagonaki.settings.LagonakiSettings
import scorex.network.{TransactionalMessagesRepo, UnconfirmedPoolSynchronizer}
import scorex.perma.api.http.PermaConsensusApiRoute
import scorex.perma.consensus.PermaConsensusModule
import scorex.perma.network.{PermacoinMessagesRepo, SegmentsSynchronizer}
import scorex.perma.settings.PermaConstants
import scorex.perma.settings.PermaConstants._
import scorex.perma.storage.AuthDataStorage
import scorex.storage.Storage
import scorex.transaction.{BalanceSheet, GenesisTransaction, SimpleTransactionModule, Transaction}
import scorex.utils.ScorexLogging

import scala.concurrent.duration._
import scala.reflect.runtime.universe._
import scala.util.Random

class Application(val settingsFilename: String) extends scorex.app.Application {

  override val applicationName = "lagonaki"

  private val appConf = ConfigFactory.load("lagonaki").getConfig("app")

  override val appVersion = {
    val raw = appConf.getString("version")
    val parts = raw.split("\\.")
    ApplicationVersion(parts(0).toInt, parts(1).toInt, parts(2).split("-").head.toInt)
  }

  override implicit lazy val settings = new LagonakiSettings(settingsFilename)

  override implicit lazy val consensusModule = {
    new File(settings.treeDir).mkdirs()
    val authDataStorage: Storage[Long, AuthDataBlock[DataSegment]] = new AuthDataStorage(Some(settings.authDataStorage))
    if (settings.isTrustedDealer) dealerSetup(authDataStorage)

    val rootHash = settings.rootHash
    actorSystem.actorOf(Props(classOf[SegmentsSynchronizer], this, rootHash, authDataStorage))
    new PermaConsensusModule(rootHash, Some(networkController))(authDataStorage)
  }

  def dealerSetup(authDataStorage: Storage[DataSegmentIndex, AuthDataBlock[DataSegment]]): Unit = {
    log.info("TrustedDealer node")
    val tree = if (Files.exists(Paths.get(settings.treeDir + MerkleTree.TreeFileName + "0.mapDB"))) {
      log.info("Get existing tree")
      new MerkleTree(settings.treeDir, PermaConstants.n, PermaConstants.segmentSize, FastCryptographicHash)
    } else {
      val datasetFile = settings.treeDir + "/data.file"
      if (!Files.exists(Paths.get(datasetFile))) {
        log.info("Generating random data set")
        val f = new RandomAccessFile(datasetFile, "rw")
        val padding: Array[Byte] = Array.fill(PermaConstants.segmentSize - 8)(0: Byte)
        f.setLength(PermaConstants.n * PermaConstants.segmentSize)

        (0L until PermaConstants.n) foreach { i =>
          f.writeLong(i)
          f.write(padding)
        }
      }
      log.info("Calculate tree")
      val tree = MerkleTree.fromFile(datasetFile, settings.treeDir, PermaConstants.segmentSize, FastCryptographicHash)
      require(tree.nonEmptyBlocks == PermaConstants.n, s"${tree.nonEmptyBlocks} == ${PermaConstants.n}")

      log.info("Put ALL data to local storage")
      new File(settings.treeDir).mkdirs()
      def addBlock(i: DataSegmentIndex): Unit = {
        authDataStorage.set(i, tree.byIndex(i).get)
        if (i > 0) addBlock(i - 1)
      }
      addBlock(PermaConstants.n - 1)
      authDataStorage.commit()

      tree
    }
    log.info("Test tree")
    require(settings.rootHash sameElements tree.rootHash, "Tree root hash differs from root hash in settings")
    require(tree.byIndex(PermaConstants.n - 1).isDefined)
    require(tree.byIndex(PermaConstants.n).isEmpty)
    val index = PermaConstants.n - 3
    val leaf = tree.byIndex(index).get
    require(leaf.check(index, tree.rootHash)(FastCryptographicHash))
  }

  override implicit lazy val transactionModule: SimpleTransactionModule = new SimpleTransactionModule()(settings, this)

  override lazy val blockStorage = transactionModule.blockStorage

  lazy val consensusApiRoute = new PermaConsensusApiRoute(this)

  override lazy val apiRoutes = Seq(
    BlocksApiRoute(this),
    TransactionsApiRoute(this),
    consensusApiRoute,
    WalletApiRoute(this),
    PaymentApiRoute(this),
    ScorexApiRoute(this),
    SeedApiRoute(this),
    PeersHttpService(this),
    AddressApiRoute(this),
    DebugApiRoute(this)
  )

  override lazy val apiTypes = Seq(
    typeOf[BlocksApiRoute],
    typeOf[TransactionsApiRoute],
    typeOf[PermaConsensusApiRoute],
    typeOf[WalletApiRoute],
    typeOf[PaymentApiRoute],
    typeOf[ScorexApiRoute],
    typeOf[SeedApiRoute],
    typeOf[PeersHttpService],
    typeOf[AddressApiRoute],
    typeOf[DebugApiRoute]
  )

  override lazy val additionalMessageSpecs = TransactionalMessagesRepo.specs ++ PermacoinMessagesRepo.specs

  //checks
  require(transactionModule.balancesSupport)
  require(transactionModule.accountWatchingSupport)

  actorSystem.actorOf(Props(classOf[UnconfirmedPoolSynchronizer], this))

}

object Application extends App with ScorexLogging {

  log.debug("Start server with args: {} ", args)
  val filename = args.headOption.getOrElse("settings.json")

  val application = new Application(filename)

  log.debug("PermaScorex has been started")
  application.run()

  if (application.wallet.privateKeyAccounts().isEmpty) application.wallet.generateNewAccounts(1)

  def testingScript(application: Application): Unit = {
    log.info("Going to execute testing scenario")
    log.info("Current state is:" + application.blockStorage.state)
    val wallet = application.wallet

    if (wallet.privateKeyAccounts().isEmpty) {
      wallet.generateNewAccounts(3)
      log.info("Generated Accounts:\n" + wallet.privateKeyAccounts().toList.map(_.address).mkString("\n"))
    }

    log.info("Executing testing scenario with accounts" +
      s"(${wallet.privateKeyAccounts().size}) : "
      + wallet.privateKeyAccounts().mkString(" "))

    require(wallet.privateKeyAccounts().nonEmpty)

    Thread.sleep(3.seconds.toMillis)

    val genesisBlock = application.blockStorage.history.genesis
    val genesisAccs = genesisBlock.transactions.flatMap(_ match {
      case gtx: GenesisTransaction =>
        Some(gtx.recipient)
      case _ =>
        log.error("Non-genesis tx in the genesis block!")
        None
    })

    def genPayment(recipient: Option[Account] = None, amtOpt: Option[Long] = None): Option[Transaction] = {
      val pkAccs = wallet.privateKeyAccounts().ensuring(_.nonEmpty)
      val senderAcc = pkAccs(Random.nextInt(pkAccs.size))
      val senderBalance = application.blockStorage.state.asInstanceOf[BalanceSheet].generationBalance(senderAcc)
      val recipientAcc = recipient.getOrElse(genesisAccs(Random.nextInt(genesisAccs.size)))
      val fee = Random.nextInt(5).toLong + 1
      if (senderBalance - fee > 0) {
        val amt = amtOpt.getOrElse(Math.abs(Random.nextLong() % (senderBalance - fee)))
        Some(application.transactionModule.createPayment(senderAcc, recipientAcc, amt, fee))
      } else None
    }

    log.info("Generate 200 transactions")
    (1 to 200) foreach (_ => genPayment())

    (1 to Int.MaxValue).foreach { _ =>
      Thread.sleep(Random.nextInt(5.seconds.toMillis.toInt))
      log.info(s"Payment created: ${genPayment()}")
    }
  }
}
