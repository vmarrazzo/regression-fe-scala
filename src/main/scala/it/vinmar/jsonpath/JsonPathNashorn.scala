package it.vinmar.jsonpath

import java.io.{IOException, InputStreamReader}
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
    * Standard requirement to access Nashorn API
    */
  val engine: ScriptEngine = (new ScriptEngineManager).getEngineByName("nashorn")
  engine.eval(new InputStreamReader(getClass.getClassLoader.getResourceAsStream("jsonpath.js")))

  /**
    * Fetch JSON data from network an handles error
    *
    * @return
    */
  def jsonFromUrl: String => Future[String] =
    (url: String) => {

      val p = Promise[String]()

      val patternUnicode = "\\\\u[0-9A-Za-z]{4}".r
      def unicodeToChar(str:String) : Char = java.lang.Integer.parseInt(str.replace("\\u",""), 16).toChar
      def replaceUnicodeChar(in:String,key2value:(String,Char)) : String = in.replaceAllLiterally(key2value._1,key2value._2.toString)

      try {
        val j = {
          val temp = scala.io.Source.fromURL(url).mkString
          val findElementAndMapping = (patternUnicode findAllIn temp)
            .map( (unicode:String) => (unicode,unicodeToChar(unicode)) )
          findElementAndMapping.foldLeft[String](temp)(replaceUnicodeChar)
        }

        p.success(j)
      }
      catch {
        case t: Throwable => p.failure(t)
      }
      p.future
    }

  /**
    * It verifies occurence of JSONPath rules into JSON Data passed.
    * It uses the selector logic.
    *
    * @return
    */
  def testJsonPathResultMultipleWithSelector(jsonData: String, jsonPaths: Map[String, String], selector: Option[String]): Map[String, Boolean] = {

    val workingCommands: Map[String, String => String] = selector match {
      case None => jsonPaths.mapValues((value: String) => (json: String) => "JSONPath(\"" + value + "\", " + json + ");")
      case Some(selectorRule) =>
        jsonPaths.mapValues((testRules: String) =>
          (json: String) => "var delta = JSONPath(\"" + selectorRule + "\", " + json + "); JSONPath(\"" + testRules + "\", delta);")
    }

    def singleCheck(key: String, command: String): Boolean = {
      try {
        val ans = engine.eval(command).asInstanceOf[ScriptObjectMirror]
        !ans.isEmpty
      } catch {
        case se: ScriptException =>
          logger.error(s"Error on Nashorn engine for $key -> ${se.getMessage}!")
          false
      }
    }

    for ((key, value) <- workingCommands) yield (key, singleCheck(key, value(jsonData)))
  }

  /**
    * It extract content of JSONPath rules from JSON Data passed
    *
    * @return
    */
  def getJsonPathResultMultiple(jsonData: String, jsonPaths: Map[String, String]): Map[String, Any] = {

    def singleCheck(jsonPath: String): Any = {
      val command: String = "JSONPath(\"" + jsonPath + "\", " + jsonData + ");"
      try {
        val ans = engine.eval(command).asInstanceOf[ScriptObjectMirror]

        if (ans.isEmpty) {
          logger.error(s"Query $jsonPath cannot return value!")
          Nil
        }
        else if (ans.size.equals(1))
          ans.get("0")
        else {
          logger.debug("Multiple results")

          import scala.collection.JavaConverters._

          ans.values().asScala.toList
        }
      } catch {
        case se: ScriptException =>
          logger.error(s"Error on Nashorn engine for $jsonPath -> ${se.getMessage}!")
          false
      }
    }

    // for each query returns a boolean with status
    jsonPaths.mapValues(singleCheck)
  }

  /**
    * It verifies occurence of JSONPath rules into JSON Data passed.
    *
    * @return
    */
  def testJsonPathMultiple(jsonData: String, jsonPaths: List[String]): List[Boolean] = {

    def singleCheck(jsonPath: String): Boolean = {
      val command: String = "JSONPath(\"" + jsonPath + "\", " + jsonData + ");"
      try {
        val ans = engine.eval(command).asInstanceOf[ScriptObjectMirror]
        !ans.isEmpty
      } catch {
        case se: ScriptException =>
          logger.error(s"Error on Nashorn engine for $jsonPath -> ${se.getMessage}!")
          false
      }
    }

    // for each query returns a boolean with status
    jsonPaths.map(singleCheck)
  }

  /**
    * Handles the presence of JSONPath element into resource associated to URL
    *
    * @return
    */
  def testJsonPathOnUrl: (String, String) => Future[Boolean] =
    (url: String, jsonPath: String) => {

      val p = Promise[Boolean]()

      try {
        val jsonData = Await.result(jsonFromUrl(url), 20.seconds)

        try {
          val command: String = "JSONPath(\"" + jsonPath + "\", " + jsonData + ");"
          val ans = engine.eval(command).asInstanceOf[ScriptObjectMirror]
          p.success(!ans.isEmpty)
        } catch {
          case se: ScriptException =>
            logger.error(s"Error on Nashorn engine -> ${se.getMessage}!")
            p.failure(se)
        }

      } catch {
        case ioe: IOException =>
          logger.error(s"IO error during fetch Json data!")
          p.failure(ioe)
        case timeout: TimeoutException =>
          logger.error(s"Timeout on fetch Json data!")
          p.failure(timeout)
        case err: Throwable =>
          logger.error(s"Failed to fetch Json data ${err.getCause}")
          p.failure(err)
      }

      p.future
    }
}