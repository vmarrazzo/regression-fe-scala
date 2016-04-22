package it.vinmar.selenium

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import it.vinmar.TestBookReader.{InputTest, MatchContent, XpathContent}
import it.vinmar.TestResult.{Failed, Passed}
import it.vinmar.TestSubStatus.SubStatus
import it.vinmar.{TestResult, TestSubStatus}
import org.scalatest.{FlatSpecLike,BeforeAndAfterAll,MustMatchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestExecutorTest(_system: ActorSystem) extends TestKit(_system)
				with ImplicitSender
				with FlatSpecLike
				with BeforeAndAfterAll
				with MustMatchers {

	behavior of "Test Executor"

	def this() = this(ActorSystem("TestExecutorTestSystem"))

	override def beforeAll {
		info("Start Test Actor System")
	}

	override def afterAll {
		info("Stop Test Actor System")
		TestKit.shutdownActorSystem(system)
		Await.result(system.terminate(), 10.seconds)
	}

	var underTest : TestActorRef[TestExecutor] = null

	it should "perform an initialization" in {

		//val grid : Option[String] = Some("http://selenium.grid:4444/wd/hub")

		//val props = Props(classOf[TestExecutor], DesiredCapabilities.chrome, grid)
		//val props = Props(classOf[TestExecutor], DesiredCapabilities.firefox, grid)

		//val props = Props(classOf[TestExecutor])

		//val props = Props(classOf[TestExecutor], DesiredCapabilities.chrome)
		//val props = Props(classOf[TestExecutor], DesiredCapabilities.firefox)

		import java.net.URL
		val grid = Some(new URL(System.getProperty("integration.test.grid", "http://whereis.selenium.grid:4444/wd/hub")))

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

		expectNoMsg(10.seconds)

		within(2.minutes) {

			retMessage = null

			underTest ! in

			retMessage = expectMsgClass(1.minutes, classOf[TestResult])

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

    val tstResult : Option[SubStatus] = TestSubStatus.toSubStatus(res0004.loadTime)

    tstResult must contain oneOf (TestSubStatus.TimeoutError, TestSubStatus.SystemError)
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

}