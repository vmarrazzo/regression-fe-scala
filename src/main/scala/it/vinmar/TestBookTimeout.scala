package it.vinmar

import akka.actor.Actor
import ManagerExecutorProtocol.{TimeoutOnTestBook, WorkIsReady}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

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

        // here sender is the manager that invoke this actor
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
