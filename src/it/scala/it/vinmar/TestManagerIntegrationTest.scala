package it.vinmar

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, MustMatchers}

import scala.concurrent.duration._
import it.vinmar.MasterWorkerProtocol._
import org.openqa.selenium.remote.DesiredCapabilities

import scala.concurrent.Await

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
    Await.result(system.terminate(), 10.seconds)
  }

  it should "perform a test book execution via grid" in {

    val testBook = TestBookReader.parseInputTestBook( "./src/it/resources/TestBookIt.xlsx", "IntegrationSheet")

    val expectedNrTest = testBook.size

    import java.net.URL
    val grid = Some(new URL(System.getProperty("integration.test.grid", "http://whereis.selenium.grid:4444/wd/hub")))

    val testTimeout = 2.minutes

    val props = Props(classOf[TestManager], DesiredCapabilities.firefox, grid, testTimeout)
    val underTest : TestActorRef[TestManager] = TestActorRef( props, name="TestManager_under_test")

    info("Send test book to under test object")

    underTest ! NewTestBook(testBook)

    val retResults = expectMsgClass(testTimeout, classOf[TestResults])

    val results : Seq[TestResult] = retResults.testResults

    results.size must be (expectedNrTest)

    TestResultWriter.generateDataSheetReport("./target/sampleManagerIntegration.xlsx", testBook, results)
  }
}
