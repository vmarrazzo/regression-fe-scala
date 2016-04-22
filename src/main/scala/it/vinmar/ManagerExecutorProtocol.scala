package it.vinmar

import TestBookReader.InputTest

object ManagerExecutorProtocol {

  // Messages to Manager
  case class NewTestBook(testBook: Seq[InputTest])
  case object TimeoutOnTestBook

  // Messages from Manager
  case class TestResults(testResults: Seq[TestResult])
  case object ManagerEncounterInitProblem

  // Messages from Executor
  // case class WorkerCreated(worker: ActorRef)
  // case class WorkerRequestsWork(worker: ActorRef)
  // case class WorkIsDone(worker: ActorRef)

  // Messages to Executor
  // case class WorkToBeDone(work: Any)
  case object WorkIsReady
  // case object NoWorkToBeDone
}
