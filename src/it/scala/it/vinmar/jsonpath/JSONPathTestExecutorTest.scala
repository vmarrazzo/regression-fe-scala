package it.vinmar.jsonpath

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import it.vinmar.TestBookReader.{InputTest, JsonPathContent}
import it.vinmar.TestResult.{Failed, Passed}
import it.vinmar.{TestResult, TestSubStatus}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, MustMatchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by vmarrazzo on 25/04/2016.
  */
class JSONPathTestExecutorTest(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with FlatSpecLike
  with BeforeAndAfterAll
  with MustMatchers {

  behavior of "JSONPath Test Executor"

  def this() = this(ActorSystem("TestExecutorTestSystem"))

  override def beforeAll {
    info("Start Test Actor System")
  }

  override def afterAll {
    info("Stop Test Actor System")
    TestKit.shutdownActorSystem(system)
    Await.result(system.terminate(), 10.seconds)
  }

  var underTest : TestActorRef[JSONPathTestExecutor] = null

  it should "performs a correct message handing" in {

    val timeout = 20.seconds

    underTest = TestActorRef( Props(classOf[JSONPathTestExecutor]), name="TestExecutor_under_test")

    underTest ! InputTest("Test_0001", JsonPathContent, "Sample test well formatted One", "http://test-mobile.mi.seat.it/featuredfilters?output=json&polygon=9.122987+45.499845%2C9.120062+45.499439%2C9.119846+45.499141%2C9.119650+45.498409%2C9.119821+45.497797%2C9.120266+45.497453%2C9.121545+45.497254%2C9.122127+45.497350%2C9.122364+45.497476%2C9.122883+45.498127%2C9.123049+45.498723%2C9.123015+45.499350%2C9.122857+45.499623%2C9.122455+45.499820%2C9.122211+45.499785%2C9.122987+45.499845&pagesize=25&client=pgmobile&mdr=json&sortby=relevance&categories=006776600&lang=it&device=android&version=5.0.3", "$..status")

    expectMsgClass(timeout, classOf[TestResult])

    underTest ! InputTest("Test_0002", JsonPathContent, "Sample test bad formatted One", "http://www.paginegialle.it", "Google Inc.")

    // with actor restart the mailbox survive ???
    val ansT0002 = expectMsgClass(timeout, classOf[TestResult])
    ansT0002.result must be(Failed)
    ansT0002.loadTime must be(TestSubStatus.SystemError.code)

    underTest ! InputTest("Test_0003", JsonPathContent, "Sample test well formatted Two", "http://test-mobile.mi.seat.it/featuredfilters?output=json&polygon=9.122987+45.499845%2C9.120062+45.499439%2C9.119846+45.499141%2C9.119650+45.498409%2C9.119821+45.497797%2C9.120266+45.497453%2C9.121545+45.497254%2C9.122127+45.497350%2C9.122364+45.497476%2C9.122883+45.498127%2C9.123049+45.498723%2C9.123015+45.499350%2C9.122857+45.499623%2C9.122455+45.499820%2C9.122211+45.499785%2C9.122987+45.499845&pagesize=25&client=pgmobile&mdr=json&sortby=relevance&categories=006776600&lang=it&device=android&version=5.0.3", "$..*[?(@property === 'device' && @ === 'android')]")

    val ansT0003 = expectMsgClass(timeout, classOf[TestResult])
    ansT0003.result must be(Passed)

    underTest ! InputTest("Test_0004", JsonPathContent, "Sample test bad formatted Two", "http://www.paginegialle.it", "Funny String")
    underTest ! InputTest("Test_0005", JsonPathContent, "Sample test well formatted Three", "http://test-mobile.mi.seat.it/featuredfilters?output=json&polygon=9.122987+45.499845%2C9.120062+45.499439%2C9.119846+45.499141%2C9.119650+45.498409%2C9.119821+45.497797%2C9.120266+45.497453%2C9.121545+45.497254%2C9.122127+45.497350%2C9.122364+45.497476%2C9.122883+45.498127%2C9.123049+45.498723%2C9.123015+45.499350%2C9.122857+45.499623%2C9.122455+45.499820%2C9.122211+45.499785%2C9.122987+45.499845&pagesize=25&client=pgmobile&mdr=json&sortby=relevance&categories=006776600&lang=it&device=android&version=5.0.3", "$..*[?(@property === 'device' && @ === 'android')]")

    val returnedSeqOne : Seq[AnyRef] = receiveN(2, timeout)

    returnedSeqOne must have size 2

    underTest ! InputTest("Test_0006", JsonPathContent, "Sample test bad formatted Three", "http://test-mobile.mi.seat.it/featuredfilters?output=json&polygon=9.122987+45.499845%2C9.120062+45.499439%2C9.119846+45.499141%2C9.119650+45.498409%2C9.119821+45.497797%2C9.120266+45.497453%2C9.121545+45.497254%2C9.122127+45.497350%2C9.122364+45.497476%2C9.122883+45.498127%2C9.123049+45.498723%2C9.123015+45.499350%2C9.122857+45.499623%2C9.122455+45.499820%2C9.122211+45.499785%2C9.122987+45.499845&pagesize=25&client=pgmobile&mdr=json&sortby=relevance&categories=006776600&lang=it&device=android&version=5.0.3", "Funny String")
    underTest ! InputTest("Test_0007", JsonPathContent, "Sample test well formatted Four", "http://test-mobile.mi.seat.it/featuredfilters?output=json&polygon=9.122987+45.499845%2C9.120062+45.499439%2C9.119846+45.499141%2C9.119650+45.498409%2C9.119821+45.497797%2C9.120266+45.497453%2C9.121545+45.497254%2C9.122127+45.497350%2C9.122364+45.497476%2C9.122883+45.498127%2C9.123049+45.498723%2C9.123015+45.499350%2C9.122857+45.499623%2C9.122455+45.499820%2C9.122211+45.499785%2C9.122987+45.499845&pagesize=25&client=pgmobile&mdr=json&sortby=relevance&categories=006776600&lang=it&device=android&version=5.0.3", "$..*[?(@property === 'device' && @ === 'android')]")

    val returnedSeqTwo : Seq[AnyRef] = receiveN(2, timeout)

    returnedSeqTwo must have size 2
  }
}
