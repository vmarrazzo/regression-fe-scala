package it.vinmar.jsonpath

import java.io.{FileReader, IOException}
import java.net.MalformedURLException
import javax.script.{ScriptEngine, ScriptEngineManager, ScriptException}

import jdk.nashorn.api.scripting.ScriptObjectMirror
import org.scalatest.{BeforeAndAfterAll, FlatSpec, MustMatchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}

class JsonPathScalaTest extends FlatSpec
  with BeforeAndAfterAll
  with MustMatchers {

  behavior of "JsonPath in Scala with Nashorn API"

  /**
    * Standard requirement to access Nashorn API
    */
  private var engine: ScriptEngine = null

  override def beforeAll(): Unit = {

    val engineManager: ScriptEngineManager = new ScriptEngineManager
    engine = engineManager.getEngineByName("nashorn")
    engine.eval(new FileReader("src/main/resources/jsonpath.js"))
  }

  it should "works with simply test" in {

    engine.eval("function sum(a, b) { return a + b; }") // Long
    val obtained = engine.eval("sum(1, 2);")
    info(s"A simple sum of integer returns $obtained")
    obtained.toString.toInt must be(3)
  }

  /**
    * This block of code contains all needs for project
    */
  private val evaluateIfExist : (String,String) => Boolean = {
    (jsonPath, jsonData) => {

      try {
        val command : String = "JSONPath(\"" + jsonPath + "\", " + jsonData + ");"
        val ans = engine.eval(command).asInstanceOf[ScriptObjectMirror]
        !ans.isEmpty
      } catch {
        case se : ScriptException => false
      }
    }
  }

  val complexCase: String = "{\n   \"store\": {\n      \"book\": [\n         {\n            \"category\": \"reference\",\n            \"author\": \"Nigel Rees\",\n            \"title\": \"Sayings of the Century\",\n            \"price\": 8.95\n         },\n         {\n            \"category\": \"fiction\",\n            \"author\": \"Evelyn Waugh\",\n            \"title\": \"Sword of Honour\",\n            \"price\": 12.99\n         },\n         {\n            \"category\": \"fiction\",\n            \"author\": \"Herman Melville\",\n            \"title\": \"Moby Dick\",\n            \"isbn\": \"0-553-21311-3\",\n            \"price\": 8.99\n         },\n         {\n            \"category\": \"fiction\",\n            \"author\": \"J. R. R. Tolkien\",\n            \"title\": \"The Lord of the Rings\",\n            \"isbn\": \"0-395-19395-8\",\n            \"price\": 22.99\n         }\n      ],\n      \"bicycle\": {\n         \"color\": \"red\",\n         \"price\": 19.95\n      }\n   }\n}"

  val patternsComplex: Map[String, Boolean] = List(
    new Tuple2("$",true),
    new Tuple2("$..author",true),
	new Tuple2("$.[?(@property !== 'store')].color", true),
    new Tuple2("$..*[?(@.color)]",true),
    new Tuple2("$..*[?(@.color=='green')]",false),
    new Tuple2("$..*[?(@.color=='red' || @.price == 8.95)]",true),
    new Tuple2("$..*[?(@.color=='red' && @.price == 8.95)]",false),
    new Tuple2("$..*[?(/of/.test(@.title) && @.price >= 10.00)]",true),
    new Tuple2("$..book[?(/Waugh/.test(@.author))].category",true),
    new Tuple2("$..book[?(/Wugh/.test(@.author))]",false),
    new Tuple2("$..book[?(/Waugh/.test(@.author))]",true),
    new Tuple2("$..*[?(@property === 'price' && @ === 12.99)]",true)
  ).toMap

  private def coreTest(jsonPaths: Map[String,Boolean], json: String) = {

    val localCheck : String => Boolean = (jsonPath: String) => evaluateIfExist.apply(jsonPath, json)
    val results = for ( (k,v) <- jsonPaths ) yield (k,localCheck(k))

    info("JSON Data : " + json)

    for ( (jsonpath, expected) <- jsonPaths ) {
      withClue(s"XX FAIL XX $jsonpath -> ") {
        results.get(jsonpath) match {
          case Some(obtained) => obtained must be(expected)
          case None => fail(s"Missing result for $jsonpath")
        }
      }

      info(s"Query JSONPath : $jsonpath")
      info(s"Result exists : ${if (expected) "YES" else "NO"}")
    }
  }

  it should "works in a complex case" in {

    coreTest(patternsComplex, complexCase)
  }

  it should "works in a complex case without pretty print" in {

    val rawComplexCase = complexCase.replaceAll("[\n|\t]", "")

    coreTest(patternsComplex, rawComplexCase)
  }

  /**
    * Handling connection
    */

  val goodUrl: String = "http://test-mobile.mi.seat.it/featuredfilters?output=json&polygon=9.122987+45.499845%2C9.120062+45.499439%2C9.119846+45.499141%2C9.119650+45.498409%2C9.119821+45.497797%2C9.120266+45.497453%2C9.121545+45.497254%2C9.122127+45.497350%2C9.122364+45.497476%2C9.122883+45.498127%2C9.123049+45.498723%2C9.123015+45.499350%2C9.122857+45.499623%2C9.122455+45.499820%2C9.122211+45.499785%2C9.122987+45.499845&pagesize=25&client=pgmobile&mdr=json&sortby=relevance&categories=006776600&lang=it&device=android&version=5.0.3"

  val goodPatterns: Map[String, Boolean] = List(
    new Tuple2("$",true),
    new Tuple2("$..status",true),
    new Tuple2("$..*[?(@property === 'sortby' && @ === 'relevance')]",true),
    new Tuple2("$.[?(@property === 'status' && @ === '200')]",true),
    new Tuple2("$..params[?(@.device === 'android')]",true),
    new Tuple2("$..*[?(@property === 'device' && @ === 'android')]",true),
    new Tuple2("$..*[?(@property === 'device' && @ !== 'symbian')]",true),
    new Tuple2("$..*[?(@property === 'device' && @ === 'symbian')]",false)
  ).toMap

  it should "works with real JSON" in {

    val jsonData = scala.io.Source.fromURL(goodUrl).mkString

    coreTest(goodPatterns, jsonData)
  }

  /**
    * Handle concurrent queries
    */

  it should "works with real JSON in concurrent scenario" in {

    val fetchJson : Future[String] = JsonPathNashorn.jsonFromUrl(goodUrl)

    try {
      coreTest(goodPatterns, Await.result(fetchJson, 20.seconds))
    } catch {
      case timeout: TimeoutException => fail(s"Timeout on fetch Json data!")
      case err : Throwable => fail(s"Failed to fetch Json data ${err.getCause}")
    }
  }

  it should "works with real JSON in concurrent scenario with error handling" in {

    val badUrl = "http://www.paginegialle.it/"

    intercept[IOException] {
      Await.result(JsonPathNashorn.testJsonPathOnUrl(badUrl,"$"), 20.seconds)
    }

    // without any valid JSONPath string, it returns false and not error
    val res = Await.result(JsonPathNashorn.testJsonPathOnUrl(goodUrl,"$//a[@class='logo']"), 20.seconds)

    res must be(false)
  }

  it should "works with multiple network queries" in {

    // that's parallel!
    goodPatterns.par.foreach {
      case (testPath, expected) => {
        val obtained : Future[Boolean] = JsonPathNashorn.testJsonPathOnUrl(goodUrl,testPath)
        withClue(s"$testPath must be ${if(expected) "IN" else "OUT"}! --- ") {
          Await.result(obtained, Duration.Inf) must be(expected)
        }
      }
    }

  }

}

