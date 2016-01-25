package it.vinmar

import org.scalatest._
import Matchers._

import scala.collection.JavaConversions._

import it.vinmar.TestBookReader._

class TestBookReaderTest extends FlatSpec with BeforeAndAfterAll with MustMatchers {

  /**
   * Comparative parameter
   */
  private val EXPECTED_ROW_NUMBERS: Integer = 15

  val sheetName : String = "TestSheet"
  val file: String = "./src/test/resources/TestBookTest.xlsx"

  it should "be able to perform a book reading" in {

    val testBook = TestBookReader.parseInputTestBook(file, sheetName)

    testBook.length should be (EXPECTED_ROW_NUMBERS)

    testBook.foreach( ( x : InputTest ) => assert( x.url.startsWith("http://") ) )
  }

  it should "be contains some specific row" in {

    val testBook = TestBookReader.parseInputTestBook(file, sheetName)

    testBook should not be (null)

    def checkIndex( index : Integer,
                    expTestId : String,
                    expTestType : TestType,
                    expDescr : String,
                    expUrl : String,
                    expRule : String) : Unit = {

      val elem = testBook.get(index)

      elem.testId should be (expTestId)
      elem.testType should be (expTestType)
      elem.description should be (expDescr)
      elem.url should be (expUrl)
      elem.rule should be (expRule)

    }

    checkIndex( 0,
                "TestId_0001",
                MatchContent,
                "Name of page owner",
                "http://it.linkedin.com/in/vincenzo-marrazzo-3060b06",
                "Vincenzo Marrazzo" )

    checkIndex( 9,
                "TestId_0010",
                XpathContent,
                "Old company logo2",
                "http://it.linkedin.com/in/vincenzo-marrazzo-3060b06",
                "//img[@alt='Altran']" )

    checkIndex( 11,
                "TestId_0012",
                XpathContent,
                "Brand logo element",
                "http://www.ansa.it",
                "//a[@class='brand-logo']" )

    checkIndex( 13,
                "TestId_0014",
                XpathContent,
                "Test to fail 2",
                "http://www.questo.dominio.impossibile",
                "//div[@id='h_inseriscizzzzzz']" )
  }

  it should "be capable to reject malformed row" in {

   val ex = intercept[IllegalArgumentException] {
          TestBookReader.parseInputTestBook(file, sheetName + "_FAIL")
      }

    ex.getMessage() should be ("The Row [Technology sub-interest] matches with more than one test type!")

  }

  it should "be capable to filter monitoring test" in {

    val testBook = TestBookReader.parseInputTestBook(file, sheetName, "MONITORING")

    testBook.length should be (3)
  }
}