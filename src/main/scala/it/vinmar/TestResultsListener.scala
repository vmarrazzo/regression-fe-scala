package it.vinmar

import akka.actor.{Actor, ActorRef, PoisonPill}
import ManagerExecutorProtocol._
import TestBookReader.InputTest
import org.slf4j.LoggerFactory

import java.util.{ Timer, TimerTask }
import java.text.SimpleDateFormat

import scala.concurrent.duration.Duration

/**
  * Application actor that handles :<br>
  *   <li> TM work time into timeout
  *   <li> Save the collected test results
  *
  * @param book
  * @param resultFileName
  * @param manager
  * @param deadline
  */
class TestResultsListener(val book: List[InputTest],
                          val resultFileName: String,
                          val manager: ActorRef,
                          val deadline: Duration) extends Actor {

  /**
    * Formatter for date time
    */
  val formatter = new SimpleDateFormat("yyyyMMdd_HHmmss")

  /**
    * The timer
    */
  val timer = new Timer(true)

  /**
    * Logger
    */
  private def logger = LoggerFactory.getLogger(this.getClass)

  override def preStart: Unit = {

    logger.info(s"Send to manager test book with ${book.size} test cases.")

    manager ! NewTestBook(book)

    logger.info(s"Test Manager deadline is ${deadline} to complete test book.")

    timer.schedule(new TimerTask {
      def run() = {

        logger.error(s"Test Manager does not complete into ${deadline} so force ending!")

        manager ! TimeoutOnTestBook

        timer.cancel
      }
    }, deadline.toMillis)
  }

  override def receive: Receive = {

    case TestResults(results) => {

      if (sender.equals(manager)) {

        logger.info(s"Received from manager test result with ${results.size} test case.")

        val outputFilename = s"./${resultFileName}_${formatter.format(java.util.Calendar.getInstance.getTime)}.xlsx"
        TestResultWriter.generateDataSheetReport( outputFilename, book, results)

        manager ! PoisonPill

        timer.cancel

        context.system.terminate()
      }
    }
    case ManagerEncounterInitProblem => {

      logger.error("Manager reports problem during startup phase so no data collected!")

      timer.cancel

      context.system.terminate()
    }
    case x: Any => {
      logger.warn(s"Received an un-handled message from ${sender.path.name}!")
      unhandled(x)
    }
  }
}
