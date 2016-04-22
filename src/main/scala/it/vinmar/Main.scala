package it.vinmar

import akka.actor.{ActorSystem, Props}
import org.slf4j.LoggerFactory
import java.net.{URI, URL}
import java.io.File

import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import org.openqa.selenium.remote.DesiredCapabilities

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

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
          val logCtx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

          val configurator = new JoranConfigurator
          configurator.setContext(logCtx)
          logCtx.reset
          configurator.doConfigure(config.logfile)
        }
        else {
          val root = LoggerFactory.getLogger("root").asInstanceOf[Logger];
          root.setLevel(Level.OFF);
        }

        val tb = TestBookReader.parseInputTestBook( config.testfile.getAbsolutePath, config.sheetname)

        // for now is hardcoded in future will be a command line argument
        val timeout: Duration = 2.hours

        val system = ActorSystem("MySystem")
        val tm = system.actorOf(Props(classOf[TestManager], DesiredCapabilities.firefox, config.grid, timeout), "MyTestManager")

        val bl = system.actorOf(Props(classOf[TestResultsListener], tb, "FE-Regress", tm, timeout), "MyBookListener")
      }
      case None => unitParser1.showUsage
    }

  }

}
