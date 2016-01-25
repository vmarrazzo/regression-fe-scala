package it.vinmar

import it.vinmar.TestBookReader.{ XpathContent, MatchContent, InputTest }
import it.vinmar.MasterWorkerProtocol._

import org.scalatest._

import akka.actor._

import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
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
    system.awaitTermination(60.seconds)
  }

  var underTest: TestActorRef[TestManager] = null

  val testBook : List[InputTest] = InputTest("Test_0001", MatchContent, "Company signature presence", "http://www.google.it", "Google Inc.") ::
    InputTest("Test_0002", XpathContent, "Brand logo element", "http://www.ansa.it", "//a[@class='brand-logo']") ::
    InputTest("Test_0003", XpathContent, "Test to fail", "http://www.subito.it", "//div[@id='h_inseriscizzzzzz']") ::
    InputTest("Test_0004", XpathContent, "Test to fail", "http://www.questo.dominio.impossibile", "//div[@id='h_inseriscizzzzzz']") ::
    InputTest("Test_0005", XpathContent, "Test to pass", "http://www.paginegialle.it/", "//a[@class='logo']") ::
    Nil

  it should "perform an initialization" in {

    val props = Props(classOf[TestManager], None)

    underTest = TestActorRef(props, name = "TestManager_under_test")

    expectNoMsg(10.seconds)

    var retResults: TestResults = null

    within(30.minutes) {

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
    val grid : Option[URL] = Some(new URL("http://localhost:4444/wd/hub"))

    val props = Props(classOf[TestManager], grid)
    val underTest : TestActorRef[TestManager] = TestActorRef( props, name="TestManager_under_test_grid_fail")

    info("Send test book to under test object")

    underTest ! NewTestBook(testBook)

    val resultMessage = receiveOne(2.minutes)

    resultMessage must be (MasterWorkerProtocol.ManagerEncounterInitProblem)
  }
}