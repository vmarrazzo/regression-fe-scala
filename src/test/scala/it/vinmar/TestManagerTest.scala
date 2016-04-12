package it.vinmar

import it.vinmar.TestBookReader.{InputTest, MatchContent, XpathContent}
import it.vinmar.MasterWorkerProtocol._
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest._
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestManagerTest(_system: ActorSystem) extends TestKit(_system) 
      with ImplicitSender 
      with FlatSpecLike 
      with BeforeAndAfterAll 
      with MustMatchers {

  behavior of "Test Manager"

  def this() = this(ActorSystem("TestManagerTestSystem"))

  override def beforeAll {
    info("Start Test Actor System")
  }

  override def afterAll {
    info("Stop Test Actor System")
    TestKit.shutdownActorSystem(system)
    Await.result( system.terminate(), 60.seconds)
  }

  var underTest: TestActorRef[TestManager] = null

  val testBook : List[InputTest] = InputTest("Test_0001", MatchContent, "Company signature presence", "http://www.google.it", "Google Inc.") ::
    InputTest("Test_0002", XpathContent, "Brand logo element", "http://www.ansa.it", "//a[@class='brand-logo']") ::
    InputTest("Test_0003", XpathContent, "Test to fail", "http://www.subito.it", "//div[@id='h_inseriscizzzzzz']") ::
    InputTest("Test_0004", XpathContent, "Test to fail", "http://www.questo.dominio.impossibile", "//div[@id='h_inseriscizzzzzz']") ::
    InputTest("Test_0005", XpathContent, "Test to pass", "http://www.paginegialle.it/", "//a[@class='logo']") ::
    Nil

  it should "perform an initialization" in {

    val testTimeout = 30.minutes

    val props = Props(classOf[TestManager], DesiredCapabilities.firefox, None, testTimeout)

    underTest = TestActorRef(props, name = "TestManager_under_test")

    expectNoMsg(10.seconds)

    var retResults: TestResults = null

    within(testTimeout) {

      retResults = null

      underTest ! NewTestBook(testBook)

      retResults = expectMsgClass(5.minutes, classOf[TestResults])

      val results : Seq[TestResult] = retResults.testResults

      results.foreach((f: TestResult) => { info(f.toString) })

      TestResultWriter.generateDataSheetReport("./target/sampleManager.xlsx", testBook, results)
    }
  }

  it should "fail when grid is not available" in {

    import java.net.URL

    val testTimeout = 20.seconds
    val fakeGrid = Some(new URL("http://fake.grid.host:4444/wd/hub"))

    val props = Props(classOf[TestManager], DesiredCapabilities.firefox, fakeGrid, 2.minutes)

    within(testTimeout) {
      val underTest = TestActorRef( props, name="TestManager_under_test_grid_fail")
      underTest ! NewTestBook(testBook)
      expectMsg(MasterWorkerProtocol.ManagerEncounterInitProblem)
      Thread.sleep(3000)
    }
  }
  
  it should "fail when is requested unsupported browser" in {

    val testTimeout = 20.seconds

    val props = Props(classOf[TestManager], DesiredCapabilities.safari, None, 2.minutes)

    within(testTimeout) {
      val underTest = TestActorRef( props, name="TestManager_under_test_unsupported_browser")
      underTest ! NewTestBook(testBook)
      expectMsg(MasterWorkerProtocol.ManagerEncounterInitProblem)
      Thread.sleep(3000)
    }
  }
}