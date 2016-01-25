package it.vinmar

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import org.scalatest.{MustMatchers, BeforeAndAfterAll, FlatSpecLike}

import scala.concurrent.duration._

import it.vinmar.MasterWorkerProtocol._

/**
  * Created by vincenzo on 20/01/16.
  */
class TestManagerIntegrationTest(_system: ActorSystem) extends TestKit(_system)
      with ImplicitSender
      with FlatSpecLike
      with BeforeAndAfterAll
      with MustMatchers {

  behavior of "Test Manager Integration"

  def this() = this(ActorSystem("TestExecutorTestSystem"))

  override def beforeAll {
    info("Start Test Actor System")
  }

  override def afterAll {
    info("Stop Test Actor System")
    TestKit.shutdownActorSystem(system)
    system.awaitTermination(10.seconds)
  }

  it should "perform an test book execution via grid" in {

    val testBook = TestBookReader.parseInputTestBook( "./src/it/resources/TestBookIt.xlsx", "IntegrationSheet")

    val expectedNrTest = testBook.size

    import java.net.URL
    val grid : Option[URL] = Some(new URL("http://localhost:4444/wd/hub"))

    val props = Props(classOf[TestManager], grid)
    val underTest : TestActorRef[TestManager] = TestActorRef( props, name="TestManager_under_test")

    info("Send test book to under test object")

    underTest ! NewTestBook(testBook)

    val retResults = expectMsgClass(2.minutes, classOf[TestResults])

    val results : Seq[TestResult] = retResults.testResults

    results.size must be (expectedNrTest)

    TestResultWriter.generateDataSheetReport("./target/sampleManagerIntegration.xlsx", testBook, results)
  }
}
