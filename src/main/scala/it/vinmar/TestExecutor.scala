package it.vinmar

import java.util.concurrent.TimeUnit

import it.vinmar.TestBookReader._
import it.vinmar.TestResult._
import it.vinmar.TestSubStatus._
import org.openqa.selenium.chrome.{ ChromeDriver, ChromeOptions }
import org.openqa.selenium.support.ui.WebDriverWait

import org.slf4j.LoggerFactory

import akka.actor.Actor
import org.openqa.selenium._
import org.openqa.selenium.firefox.{ FirefoxDriver, FirefoxProfile }
import org.openqa.selenium.remote.{ RemoteWebDriver, DesiredCapabilities }

import java.net.URL

class TestExecutor(val desiredBrowser: DesiredCapabilities, val grid: Option[URL] = None) extends Actor /* logger akka ? */ {

  /**
   *
   */
  private var driver: WebDriver = null

  def this() = this(DesiredCapabilities.firefox, None)
  def this(browser: DesiredCapabilities) = this(browser, None)
  def this(grid: Option[URL]) = this(DesiredCapabilities.firefox, grid)

  /* logger akka ? */
  import TestExecutor.{logger => log}

  /**
   *
   */
  override def preStart = {

    log.info("Init new TestExecutor")

    if (driver != null) {

      log.debug("Find an existing WedDriver instance so quit it before init a new one.")

      driver.quit
    }

    driver = TestExecutor.initDriver(desiredBrowser, grid)
  }

  /**
   *
   */
  override def postStop {

    log.info("Close TestExecutor")

    driver.quit

    driver = null
  }

  /**
   *
   * @return
   */
  override def receive = {

    case in: InputTest => sender ! {
      val locType = in.testType

      import it.vinmar.TestExecutor._

      logger.info(s"Received ${in.testId}")

      val wait = new WebDriverWait( driver, MAX_LOAD_TIME)

      val loadResult = wait.until(new Time2DisplayCondition( in.url, locType match {
        case MatchContent => Time2DisplayCondition.MatchContextLocator(in.rule)
        case XpathContent => By.xpath(in.rule)
        case e : Any => {
          val message = s"This executor cannot handle this kind of testcase ${e.getClass}"
          log.error(message)
          throw new IllegalArgumentException(message)
        }
      }))

      val resp: TestResult = {
        if (isErrorSubStatus(loadResult)) {

          logger.debug(s"Test failed on loading phase : ${in.testId}")

          new TestResult(in.testId, Failed, loadResult, None)
        } else {

          logger.debug(s"Execute test ${in.testId} as ${locType}")

          new TestResult(in.testId, if ( loadResult > 0 ) Passed else Failed, loadResult, None)
        }
      }

      logger.info(s"Send test result of ${in.testId}")

      resp
    }
    case x: Any => {
      log.warn(s"Received an un-handled message from ${sender.path.name}!")
      unhandled(x)
    }
  }

}

/**
 * Companion object with static facilities
 */
object TestExecutor {

  /**
   * Logger
   */
  def logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Timeout on page loading
   */
  val MAX_LOAD_TIME: Long = 20

  /**
   * It returns a properly initialized WebDriver instance
   *
   * @return WebDriver
   */
  private def initDriver(browser: DesiredCapabilities, grid: Option[URL]): WebDriver = {

    val driver: WebDriver = grid match {

      case Some(grid) => {
        logger.debug(s"Create RemoteWebDriver with ${browser}")
        logger.debug(s"Selenium Grid passed is ${grid.toString}")

        new RemoteWebDriver( grid, browser)
      }
      case None => {

        logger.debug(s"Create a WebDriver with ${browser}")

        import org.openqa.selenium.remote.DesiredCapabilities._

        if (browser.equals(firefox)) {
          val profile: FirefoxProfile = new FirefoxProfile
          profile.setPreference("app.update.enabled", false)
          profile.setEnableNativeEvents(true)
          browser.setCapability(FirefoxDriver.PROFILE, profile)
          new FirefoxDriver(firefox, browser)
        } else if (browser.equals(chrome)) {
          val options: ChromeOptions = new ChromeOptions
          options.addArguments("start-maximized")
          new ChromeDriver(options)
        } else {
          val message = s"Unsupported browser ${browser}"
          logger.error(message)
          throw new IllegalArgumentException(message)
        }

      }

    }

    // the real timeout on browser in enlarged
    val lowLevelTimeout = MAX_LOAD_TIME + 10

    logger.debug("Timeout on page loading is " + lowLevelTimeout + " seconds.")
    driver.manage().timeouts().pageLoadTimeout(lowLevelTimeout, TimeUnit.SECONDS)

    logger.debug("Timeout on javascript execution is " + lowLevelTimeout / 10 + " seconds.")
    driver.manage().timeouts().setScriptTimeout(lowLevelTimeout / 10, TimeUnit.SECONDS)

    driver.manage().window().maximize

    driver
  }
}