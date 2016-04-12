package it.vinmar

import it.vinmar.TestBookReader.InputTest
import org.openqa.selenium.remote.DesiredCapabilities
import org.slf4j.LoggerFactory

import akka.actor._
import akka.routing._

import akka.actor.SupervisorStrategy._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

import java.net.URL

object MasterWorkerProtocol {

  // Messages to Master
  case class NewTestBook(testBook: Seq[InputTest])
  case object TimeoutOnTestBook

  // Messagges from Master
  case class TestResults(testResults: Seq[TestResult])
  case object ManagerEncounterInitProblem

  // Messages from Workers
  // case class WorkerCreated(worker: ActorRef)
  // case class WorkerRequestsWork(worker: ActorRef)
  // case class WorkIsDone(worker: ActorRef)

  // Messages to Workers
  // case class WorkToBeDone(work: Any)
  case object WorkIsReady
  // case object NoWorkToBeDone
}

/**
 * This actor is used to force TestManager Timeout to avoid resource locking for long time
 *
 * @param timeout
 */
class TestBookTimeout(timeout: FiniteDuration = 10.minutes) extends Actor {

  /**
   *
   */
  private var timeoutStarted: Boolean = false

  import MasterWorkerProtocol._

  /**
   * Logger
   */
  private def logger = LoggerFactory.getLogger(this.getClass)

  override def preStart : Unit = {

    logger.trace(s"Init TestManager with a timeout of ${timeout.toString}")
  }

  override def receive: Receive = {

    case WorkIsReady => {
      if (!timeoutStarted) {

        logger.trace(s"TestBookTimeout will stop @ ${timeout.toString}")

        import scala.concurrent.ExecutionContext.Implicits.global

        context.system.scheduler.scheduleOnce(timeout, sender, TimeoutOnTestBook)

        timeoutStarted = true
      }
    }
    case x: Any => {
      logger.warn(s"Received an un-handled message from ${sender.path.name}!")
      unhandled(x)
    }
  }
}

/**
 * This is the core actor that manages the test book execution
 */
class TestManager(val desiredBrowser: DesiredCapabilities, val grid: Option[URL] = None, val timeout: Duration = 60.minutes) extends Actor {

  def this(grid: Option[URL]) = this(DesiredCapabilities.firefox, grid)
  def this(desiredBrowser: DesiredCapabilities) = this(desiredBrowser, None)

  import MasterWorkerProtocol._

  /**
   * Logger
   */
  private def logger = LoggerFactory.getLogger(this.getClass)

  /**
   *
   */
  private val nrTestExecutors: Int = 5

  /**
   *
   */
  private var teRouter: ActorRef = null

  /**
   * This is the actor that stops all activities if timeout occurs
   */
  private var timeoutActor: ActorRef = null

  /**
   * This is the actors that ask for test execution and will receive test results
   */
  private var testSponsor: ActorRef = null

  /**
   *
   */
  private val results: ArrayBuffer[TestResult] = ArrayBuffer.empty[TestResult]

  /**
   *
   */
  private var resCounter: Int = 0

  /**
   *
   */
  override def preStart : Unit = {

    logger.info("Init new TestManager")

    logger.debug(s"Started Manager with $nrTestExecutors executors.")
    grid match {
      case Some(url) => logger.debug(s"Selenium Grid @ ${url.toString}")
      case None => logger.debug("Local environment browser monitoring")
    }

    teRouter = context.actorOf(Props(classOf[TestExecutor], desiredBrowser, grid)
                      .withRouter(RoundRobinPool(nrTestExecutors)), name = "workerRouter")

    logger.info("Workers are initialized")

    // after executors start TM generates a child to protect long time execution sending a timeout message
    timeoutActor = context.actorOf(Props(classOf[TestBookTimeout], timeout))
  }

  /**
    * Customized supervisor strategy, when TEs cannot start correctly halt TM and warn on Selenium trouble
    *
    * @return
    */
  override def supervisorStrategy = OneForOneStrategy(maxNrOfRetries = nrTestExecutors, withinTimeRange = 1 minute) {
    case _: ActorInitializationException      => {

      logger.error("Error executors initialization!")

      grid match {
        case Some(url) => logger.error(s"Verify the reliability of Selenium Grid ${url.toString}.")
        case None => logger.error("Verify the local environment if support browser istances.")
      }

      if ( testSponsor != null )
        testSponsor ! ManagerEncounterInitProblem

      Stop
    }
    case _: Exception                => Escalate
  }

  /**
   *
   * @return
   */
  override def receive = {

    case NewTestBook(tb) => {

      logger.debug(s"Received an input test list of ${tb.size} cases.")

      testSponsor = sender

      resCounter = tb.size

      tb.foreach(teRouter ! _)

      timeoutActor ! WorkIsReady
    }
    case t: TestResult => {
      logger.debug(s">>>> Received ${t.testId} result.")

      results.append(t)

      resCounter -= 1

      logger.debug(s"#### Waiting for other ${resCounter} test result")

      if (resCounter == 0) {

        logger.debug(s">>>> Auto termination procedure")

        testSponsor ! TestResults(results.toList)
        teRouter ! akka.actor.PoisonPill

        Stop
      }
    }
    case TimeoutOnTestBook => {
      logger.warn(s">>>> Forced termination by timeout")

      testSponsor ! TestResults(results.toList)
      teRouter ! akka.actor.PoisonPill

      Stop
    }
    case x: Any => {
      logger.warn(s"Received an un-handled message from ${sender.path.name}!")
      unhandled(x)
    }
  }
}
