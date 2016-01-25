package it.vinmar

import java.net.URL

import it.vinmar.TestBookReader.InputTest
import it.vinmar.TestResult.Passed
import it.vinmar.TestResult.Failed
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import org.scalatest._

import it.vinmar.TestBookReader._

import akka.actor._

import akka.testkit.{TestProbe, ImplicitSender, TestKit, TestActorRef}
import scala.concurrent.duration._

class TestExecutorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FlatSpecLike with BeforeAndAfterAll with MustMatchers {

	behavior of "Test Executor"

	def this() = this(ActorSystem("TestExecutorTestSystem"))

	override def beforeAll {
		info("Start Test Actor System")

	}

	override def afterAll {
		info("Stop Test Actor System")
		TestKit.shutdownActorSystem(system)
		system.awaitTermination(10.seconds)
	}

	var underTest : TestActorRef[TestExecutor] = null

	val grid : Option[URL] = Some(new URL("http://localhost:4444/wd/hub"))

	it should "perform an initialization" in {

		//val grid : Option[String] = Some("http://selenium.grid:4444/wd/hub")

		//val props = Props(classOf[TestExecutor], DesiredCapabilities.chrome, grid)
		//val props = Props(classOf[TestExecutor], DesiredCapabilities.firefox, grid)

		//val props = Props(classOf[TestExecutor])

		//val props = Props(classOf[TestExecutor], DesiredCapabilities.chrome)
		//val props = Props(classOf[TestExecutor], DesiredCapabilities.firefox)

		val props = Props(classOf[TestExecutor], grid)

		underTest = TestActorRef( props, name="TestExecutor_under_test")

	}

	/**
	 * Core test that handles in/out with under test object
	 *
	 * @param in
	 * @return
	 */
	private def coreTest(in : InputTest) : TestResult = {

		var retMessage : TestResult = null

		expectNoMsg(5.seconds)

		within(2.minutes) {

			retMessage = null

			underTest ! in

			retMessage = expectMsgClass(10.seconds, classOf[TestResult])

			info(retMessage.toString)
		}

		retMessage
	}

	it should "perform correct message exchange" in {

		info("First a MatchContent test")

		coreTest(InputTest("Test_0001", MatchContent, "Company signature presence", "http://www.google.it", "Google Inc.")).result must be (Passed)

		info("Second a XpathContent test")

		coreTest(InputTest("Test_0002", XpathContent, "Brand logo element", "http://www.ansa.it", "//a[@class='brand-logo']")).result must be (Passed)

		info("Third a test that fail")

		coreTest(InputTest("Test_0003", XpathContent, "Test to fail", "http://www.subito.it", "//div[@id='h_inseriscizzzzzz']")).result must be (Failed)

		info("Fourth a test that link does not exist")

		val res0004 : TestResult = coreTest(	InputTest(	"Test_0004", XpathContent, "Test to fail", "http://www.questo.dominio.impossibile", "//div[@id='h_inseriscizzzzzz']"))

		res0004.result must be (Failed)

		TestSubStatus.toSubStatus(res0004.loadTime) match {
			case None => fail("Test_0004 must return a TimeoutError!")
			case Some(x) => x must be (atLeastOneOf(TestSubStatus.TimeoutError, TestSubStatus.SystemError))
		}
	}

	it should "be terminated via message" in {

		val probe = new TestProbe(system)
		probe.watch(underTest)

		try {
			underTest ! akka.actor.PoisonPill
		}
		catch {
			case akka.actor.ActorKilledException(message) => {
				if ( message.equals("PoisonPill") )
					info("TestExecutor_under_test correctly stopped.")
			}
		}

		probe.expectMsgPF(2.seconds){ case Terminated(actorRef) => true }
	}

	it should "can be created via default Props" in {

		info("Create a new one with default configuration")
		val props = Props(classOf[TestExecutor])
		underTest = TestActorRef( props, name="TestExecutor_under_test")

		info("Fifth a test that pass")

		coreTest(InputTest("Test_0005", XpathContent, "Test to pass", "http://www.paginegialle.it/", "//a[@class='logo']")).result must be (Passed)

		info("Sixth a test that fail")

		coreTest(InputTest("Test_0006", MatchContent, "Test to fail", "http://www.tuttocitta.it/", "unexisting_string")).result must be (Failed)

		underTest ! 0L

		expectNoMsg(5.seconds)
	}

}