package it.vinmar

import it.vinmar.TestResult.{ExitStatus, Passed}
import org.scalatest.Matchers._
import org.scalatest._

class TestResultTest extends FlatSpec with BeforeAndAfterAll with MustMatchers {

  behavior of "TestResult"

  it should "be able to identify the code related to TestSubStatus" in {

    import TestSubStatus._

    isErrorSubStatus(TimeoutError.code) should be (true)
    isErrorSubStatus(SeleniumError.code) should be (true)
    isErrorSubStatus(SystemError.code) should be (true)
    isErrorSubStatus(JavaScriptError.code) should be (true)

    isErrorSubStatus(0L) should be (false)
  }

  it should "be able to parse network resources errors" in {

    def createAndEvaluate(status : ExitStatus, in: Option[Map[String, Int]]): Boolean = new TestResult("Test_XXX", status, 2345, in).errorNetResources

    val noErrorMap: Map[String, Int] = Map[String, Int](  "correct1" -> 200,
													      "correct2" -> 201,
													      "correct3" -> 300)

    createAndEvaluate(Passed, Some(noErrorMap)) should be (false)

    createAndEvaluate(TestResult.Failed, Some(noErrorMap + ("incorrectClient" -> 400))) should be (true)
    createAndEvaluate(Passed, Some(noErrorMap + ("incorrectServer" -> 500))) should be (true)
    createAndEvaluate(TestResult.Failed, Some(noErrorMap + ("incorrectClient" -> 400) + ("incorrectServer" -> 500))) should be (true)

    createAndEvaluate(TestResult.Failed, None ) should be (false)
    createAndEvaluate(TestResult.Failed, Some(Map.empty[String,Int]) ) should be (false)
  }
}