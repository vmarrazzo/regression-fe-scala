package it.vinmar.jsonpath

import java.io.FileReader
import javax.script.{ScriptEngine, ScriptEngineManager, ScriptException}

import jdk.nashorn.api.scripting.ScriptObjectMirror
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.duration._

/**
  * Nashorn wrapper to interact with jsonpath library
  */
object JsonPathNashorn {

    /**
      * Logger
      */
    def logger = LoggerFactory.getLogger(this.getClass)



    /**
      * Fetch JSON data from network an handles error
      *
      * @return
      */
    def jsonFromUrl : String => Future[String] =
      (url: String) => {

        val p = Promise[String]()

        try {
          val j = scala.io.Source.fromURL(url).mkString
          p.success(j)
        }
        catch {
          case t:Throwable => p.failure(t)
        }
        p.future
      }

    /**
      * Handles the presence of JSONPath element into resource associated to URL
      *
      * @return
      */
    def testJsonPathOnUrl : (String,String) => Future[Boolean] =
      (url: String, jsonPath: String) => {

        val p = Promise[Boolean]()

        try {
          val jsonData = Await.result(jsonFromUrl(url), 20.seconds)

          try {
            /**
              * Standard requirement to access Nashorn API
              */
            val engineManager: ScriptEngineManager = new ScriptEngineManager
            val engine : ScriptEngine = engineManager.getEngineByName("nashorn")
            engine.eval(new FileReader("src/main/resources/jsonpath.js"))

            val command : String = "JSONPath(\"" + jsonPath + "\", " + jsonData + ");"
            val ans = engine.eval(command).asInstanceOf[ScriptObjectMirror]
            p.success(!ans.isEmpty)
          } catch {
            case se : ScriptException => {
              logger.error(s"Error on Nashorn engine -> ${se.getMessage}!")
              p.failure(se)
            }
          }

        } catch {
          case timeout: TimeoutException => {
            logger.error(s"Timeout on fetch Json data!")
            p.failure(timeout)
          }
          case err : Throwable => {
            logger.error(s"Failed to fetch Json data ${err.getCause}")
            p.failure(err)
          }
        }

        p.future
      }
}
