package it.vinmar

import akka.actor._
import org.slf4j.LoggerFactory
import it.vinmar.MasterWorkerProtocol.{ManagerEncounterInitProblem, TestResults, TimeoutOnTestBook, NewTestBook}
import it.vinmar.TestBookReader.InputTest

import java.net.{ URI, URL}
import java.io.File

/**
  * Created by vincenzo on 21/01/16.
  */
object Main {

  /**
    * Main launcher.
    *
    * @param args
    */
  def main(args: Array[String]): Unit = {

    case class Config(testfile: File = new File("./InputTest.xlsx"), sheetname: String = "InputSheet",
                     monitoring: String = "", grid: Option[URL] = None, testprofile: File = null,
                      logfile: File = null, zipstatistic: Boolean = false)

    val unitParser1 = new scopt.OptionParser[Config]("Regression Front-End") {
      head("Regression Front-End", "0.1.0")
      opt[File]("testfile") required() valueName("<file>") action( (f,c) =>
        c.copy(testfile = f) ) text("Input file *.xlsx")
      opt[String]("sheetname") required() valueName("<sheet>") action( (s,c) =>
        c.copy(sheetname = s) ) text("Input sheet with testcase")
      opt[String]("monitoring") optional() valueName("<monitoring>") action( (m,c) =>
        c.copy(monitoring = m) ) text("Monitoring filter for testcase (optional)")
      opt[URI]("grid") optional() valueName("<grid_hub>") action( (u,c) =>
        c.copy(grid = if (u != null) Some(u.toURL) else None )) text("Selenium Grid Hub (optional)")
      opt[File]("testprofile") optional() valueName("<dir>") action( (p,c) =>
        c.copy(testprofile = p) ) text("Browser profile for test execution (optional)")
      opt[File]("logfile") optional() valueName("<file>") action( (l,c) =>
        c.copy(logfile = l)) text("Log file (optional)")
      opt[Boolean]("zipstatistic") optional() action( (z,c) =>
        c.copy(zipstatistic = true)) text("Enable zip network statistics")
      help("help")
    }

    // parser.parse returns Option[C]
    unitParser1.parse(args, Config()) match {
      case Some(config) => {

        if (config.logfile != null) {

          import ch.qos.logback.classic.LoggerContext
          import ch.qos.logback.classic.joran.JoranConfigurator

          val logCtx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

          val configurator = new JoranConfigurator
          configurator.setContext(logCtx)
          logCtx.reset
          configurator.doConfigure(config.logfile)
        }
        else {

          import ch.qos.logback.classic.Level
          import ch.qos.logback.classic.Logger

          val root = LoggerFactory.getLogger("root").asInstanceOf[Logger];
          root.setLevel(Level.OFF);
        }

        val tb = TestBookReader.parseInputTestBook( config.testfile.getAbsolutePath, config.sheetname)

        val system = ActorSystem("MySystem")
        val tm = system.actorOf(Props(classOf[TestManager], config.grid), "MyTestManager")

        val bl = system.actorOf(Props(classOf[BookListner], tb, tm), "MyBookListner")
      }
      case None => unitParser1.showUsage
    }


    /**
      * Application actor that handles :<br>
      *   <li> TM work time into timeout
      *   <li> Save the collected test results
      *
      * @param book
      * @param manager
      */
    class BookListner(val book: List[InputTest], val manager: ActorRef) extends Actor {

      import java.util.{ Timer, TimerTask }
      import scala.concurrent.duration.Duration
      import scala.concurrent.duration._
      import java.text.SimpleDateFormat

      val formatter = new SimpleDateFormat("yyyyMMdd_HHmmss")

      val timer = new Timer(true)
      val timeout : Duration = 30 minutes

      /**
        * Logger
        */
      private def logger = LoggerFactory.getLogger(this.getClass)

      override def preStart: Unit = {

        logger.info(s"Send to manager test book with ${book.size} test cases.")

        manager ! NewTestBook(book)

        logger.info(s"Test Manager timeout is ${timeout} to complete test book.")

        timer.schedule(new TimerTask {
          def run() = {

            logger.error(s"Test Manager does not complete into ${timeout} so force ending!")

            manager ! TimeoutOnTestBook

            timer.cancel
          }
        }, timeout.toMillis)
      }

      override def receive: Receive = {

        case TestResults(results) => {

          if (sender.equals(manager)) {

            logger.info(s"Received from manager test result with ${results.size} test case.")

            val outputFilename = s"./FE-Regress_${formatter.format(java.util.Calendar.getInstance.getTime)}.xlsx"
            TestResultWriter.generateDataSheetReport( outputFilename, book, results)

            manager ! PoisonPill

            timer.cancel

            context.system.shutdown()
          }
        }
        case ManagerEncounterInitProblem => {

          logger.error("Manager reports problem during startup phase so no data collected!")

          timer.cancel

          context.system.shutdown()
        }
        case x: Any => {
          logger.warn(s"Received an un-handled message from ${sender.path.name}!")
          unhandled(x)
        }
      }
    }

  }

}
