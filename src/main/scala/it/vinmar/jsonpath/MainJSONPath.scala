package it.vinmar.jsonpath

import java.io.{File, IOException}

import akka.actor.SupervisorStrategy.{Escalate, Stop}
import akka.actor.{Actor, ActorInitializationException, ActorRef, ActorSystem, OneForOneStrategy, Props}
import akka.routing.RoundRobinPool
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import it.vinmar.ManagerExecutorProtocol._
import it.vinmar.TestBookReader.{InputTest, JsonPathContent, TestType}
import it.vinmar.TestResult.{Failed, Passed}
import it.vinmar.TestSubStatus.SystemError
import it.vinmar.{TestBookReader, TestBookTimeout, TestResult, TestResultsListener}
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

/**
  * Created by vmarrazzo on 21/04/2016.
  */
object MainJSONPath {

  /**
    * Main launcher.
    *
    * @param args
    */
  def main(args: Array[String]): Unit = {

    case class Config(testfile: File = new File("./InputTest.xlsx"),
                      sheetname: String = "InputSheet", logfile: File = null)

    val unitParserJSONPath = new scopt.OptionParser[Config]("Regression Front-End") {
      head("Regression Back-End JSONPath", "0.2.0-SNAPSHOT")
      opt[File]("testfile") required() valueName("<file>") action( (f,c) =>
        c.copy(testfile = f) ) text("Input file *.xlsx")
      opt[String]("sheetname") required() valueName("<sheet>") action( (s,c) =>
        c.copy(sheetname = s) ) text("Input sheet with testcase")
      opt[File]("logfile") optional() valueName("<file>") action( (l,c) =>
        c.copy(logfile = l)) text("Log file (optional)")
      help("help")
    }

    unitParserJSONPath.parse(args, Config()) match {
      case Some(config) =>

        if (config.logfile != null) {
          val logCtx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

          val configurator = new JoranConfigurator
          configurator.setContext(logCtx)
          logCtx.reset
          configurator.doConfigure(config.logfile)
        }
        else {
          val root = LoggerFactory.getLogger("root").asInstanceOf[Logger]
          root.setLevel(Level.OFF)
        }

        // new test type!
        implicit val supportedTestType : List[TestType] = List(JsonPathContent)
        val tb = TestBookReader.parseInputTestBook( config.testfile.getAbsolutePath, config.sheetname)

        // for now is hardcoded in future will be a command line argument
        val timeout: Duration = 2.hours

        val system = ActorSystem("MySystem")
        val tm = system.actorOf(Props(classOf[JSONPathTestManager], timeout), "MyTestManager")
        val bl = system.actorOf(Props(classOf[TestResultsListener], tb, "BE-JSONPath-Regress", tm, timeout), "MyBookListener")
      case None => unitParserJSONPath.showUsage
    }
  }

}

/**
  *
  *
  *
  *
  *
  *
  *
  * @param timeout
  */
class JSONPathTestManager(val timeout: Duration = 60.minutes) extends Actor {

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

    teRouter = context.actorOf(Props(classOf[JSONPathTestExecutor])
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
  override def supervisorStrategy = OneForOneStrategy(maxNrOfRetries = nrTestExecutors,
                                                      withinTimeRange = 1.minute) {
    case _: ActorInitializationException      =>

      logger.error("Error executors initialization!")

      if ( testSponsor != null )
        testSponsor ! ManagerEncounterInitProblem

      Stop
    case _: Exception                => Escalate
  }

  /**
    *
    * @return
    */
  override def receive = {

    case NewTestBook(tb) =>

      logger.debug(s"Received an input test list of ${tb.size} cases.")
      testSponsor = sender
      resCounter = tb.size
      tb.foreach(teRouter ! _)
      timeoutActor ! WorkIsReady

    case t: TestResult =>

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

    case TimeoutOnTestBook =>

      logger.warn(s">>>> Forced termination by timeout")
      testSponsor ! TestResults(results.toList)
      teRouter ! akka.actor.PoisonPill

      Stop

    case x: Any =>
      logger.warn(s"Received an un-handled message from ${sender.path.name}!")
      unhandled(x)
  }
}


/**
  *
  *
  *
  *
  *
  *
  *
  */
class JSONPathTestExecutor extends Actor {

  /**
    * Logger
    */
  private def logger = LoggerFactory.getLogger(this.getClass)

  /**
    *
    */
  override def preStart = {

    logger.info("Init new TestExecutor")
  }

  /**
    *
    */
  override def postStop {

    logger.info("Close TestExecutor")
  }

  /**
    *
    * @return
    */
  override def receive = {

    case in: InputTest => sender ! {
      val locType = in.testType

      logger.info(s"Received ${in.testId}")

      val resp: TestResult = {

        locType match {
          case JsonPathContent =>

            logger.debug(s"Execute test ${in.testId} as $locType")
            try {
              val obtained = Await.result(  JsonPathNashorn.testJsonPathOnUrl(in.url,in.rule),
                20.seconds)

              new TestResult(in.testId, if ( obtained ) Passed else Failed, 0L, None)
            }
            catch {
              case ioe: IOException => new TestResult(in.testId, Failed, SystemError.code, None)
            }

          case e : Any =>
            val message = s"This executor cannot handle this kind of testcase ${e.getClass}"
            logger.error(message)
            throw new IllegalArgumentException(message)
        }
      }

      logger.info(s"Send test result of ${in.testId}")

      resp
    }
    case x: Any =>
      logger.warn(s"Received an un-handled message from ${sender.path.name}!")
      unhandled(x)

  }

}