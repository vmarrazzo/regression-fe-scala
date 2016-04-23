package it.vinmar.jsonpath

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import it.vinmar.ManagerExecutorProtocol.{NewTestBook, TestResults}
import it.vinmar.TestBookReader.{JsonPathContent, TestType}
import it.vinmar.{TestBookReader, TestResult, TestResultWriter}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, MustMatchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by vincenzo on 23/04/16.
  */
class JSONPathTestManagerIntegrationTest(_system: ActorSystem) extends TestKit(_system)
      with ImplicitSender
      with FlatSpecLike
      with BeforeAndAfterAll
      with MustMatchers {

  behavior of "JSONPath Test Manager Integration"

  def this() = this(ActorSystem("TestExecutorTestSystem"))

  override def beforeAll {
    info("Start Test Actor System")
  }

  override def afterAll {
    info("Stop Test Actor System")
    TestKit.shutdownActorSystem(system)
    Await.result(system.terminate(), 10.seconds)
  }

  it should "perform a test book execution" in {

    implicit val supportedTestType : List[TestType] = List(JsonPathContent)

    val testBook = TestBookReader.parseInputTestBook( "./src/it/resources/JSONPathTestBookIt.xlsx", "IntegrationSheet")

    val testTimeout = 2.minutes

    val props = Props(classOf[JSONPathTestManager], testTimeout)
    val underTest : TestActorRef[JSONPathTestManager] = TestActorRef( props, name="TestManager_under_test")

    info("Send test book to under test object")

    underTest ! NewTestBook(testBook)

    val retResults = expectMsgClass(testTimeout, classOf[TestResults])

    val results : Seq[TestResult] = retResults.testResults

    results must have size testBook.size

    TestResultWriter.generateDataSheetReport("./target/JSONPathIntegrationResults.xlsx", testBook, results)
  }
}
