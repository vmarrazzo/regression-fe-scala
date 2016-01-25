package it.vinmar

import org.eclipse.jetty.http.HttpStatus

/**
 * Test result enumeration
 */
object TestResult {

  sealed trait ExitStatus

  /**
   * Passed status
   */
  case object Passed extends ExitStatus

  /**
   * Failed status
   */
  case object Failed extends ExitStatus
}

/**
 * Test sub status will be assigned into "loadTime" properties
 */
object TestSubStatus {

  sealed trait SubStatus { def code: Long }
  case object TimeoutError extends SubStatus { val code = -1L }
  case object SeleniumError extends SubStatus { val code = -2L }
  case object SystemError extends SubStatus { val code = -3L }
  case object JavaScriptError extends SubStatus { val code = -4L }

  val errorValues: List[SubStatus] = List(TimeoutError, SeleniumError, SystemError, JavaScriptError)

  def isErrorSubStatus(value: Long): Boolean = !errorValues.filter(_.code.equals(value)).isEmpty

  def toSubStatus(value: Long): Option[SubStatus] = {
    val temp = errorValues.filter(_.code.equals(value))
    temp.size match {
      case 0 => None
      case 1 => Some(temp.head)
      case _ => throw new IllegalStateException("New mathematical situation!")
    }
  }
}

import TestResult._

/**
 * Test result class contains all information archived by executed test
 */
class TestResult(val testId: String, val result: ExitStatus, val loadTime: Long, val netResources: Option[Map[String, Int]]) {

  /**
   * It returns a boolean with true if current test execution contains
   * network resources with http status 4XX or 5XX
   */
  def errorNetResources: Boolean = {

    if (netResources == None || netResources.get.isEmpty) {
      false
    }
    else {
      !(netResources.get.values.filter(x => HttpStatus.isServerError(x) || HttpStatus.isClientError(x)).isEmpty)
    }
  }

  override def toString : String = {

    def loadTimeField: String = {

      import TestSubStatus._

      toSubStatus(loadTime) match {
        case None => s"${loadTime.toString} ms"
        case Some(x) => x.toString
      }
    }

    s"${testId} - ${result} in ${loadTimeField}"
  }
}
