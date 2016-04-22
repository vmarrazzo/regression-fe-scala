package it.vinmar

import java.io.FileInputStream

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory

import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._

/**
 * This singleton has the goal to return a list from a parsed MS Excel file
 */
object TestBookReader {

  /**
   * Logger
   */
  private def logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Default sheet name
   */
  private val DEFAULT_SHEETNAME_ : String = "Sheet-Test"

  /**
   * Default value when no monitoring
   */
  private val NO_MONITORING_ : String = "NoMonitoringIsRequested"

  /**
    * Default supported TestType list
    */
  implicit val supportedTestType : List[TestType] = List(MatchContent, XpathContent)

  /**
   * Parse the input MS Excel file and return a compliant test book
   *
   * @param fileName is the absolute path to MS Excel file
   * @param sheetName is the sheet where test book is contained ( optional )
   * @param monitoring is the filter when execution is for monitoring ( optional )
   *
   * @return List[GenericTest] with parsed test book
   */
  def parseInputTestBook(fileName : String,
                         sheetName : String = DEFAULT_SHEETNAME_,
                         monitoring : String = NO_MONITORING_)
                        (implicit supportedTestType : List[TestType]) : List[InputTest] = {

    logger.info("Start MS Excel parsing")
    logger.info(s"File name -> ${fileName}")
    logger.info(s"Sheet -> ${sheetName}")

    val wb : Workbook = WorkbookFactory.create(new FileInputStream(fileName))
    val sheet : Sheet = wb.getSheet(sheetName)

    var count: Integer = 0

    def generateNextId: String = {
      count += 1
      s"TestId_%04d".formatLocal(java.util.Locale.US, count)
    }

    val testTypePreProcessor = new TestTypePreProcessor(supportedTestType)

    val testBook: List[InputTest] = sheet.rowIterator
      .filter((r: Row) => !testTypePreProcessor.identifyTestTypeFromRow(r, monitoring).equals(None))
      .map((f: Row) => testTypePreProcessor.createNewGenericTest(generateNextId, f)).toList

    logger.info(s"Created a book of ${testBook.size} tests.")

    testBook
  }

  /**
   * Test Type from IN file point of view
   *
   * @param urlColumn
   * @param ruleColumn
   */
  sealed abstract class TestType(val urlColumn : Integer, val ruleColumn : Integer) {

    /**
     * This method checks if passed row is valid to extract data from pointed cells
     *
     * @param row
     * @return
     */
    def validRow4TestType(row: Row): Boolean = {

      val url = row.getCell(urlColumn)
      val rule = row.getCell(ruleColumn)

      val notNullCell = url != null && rule != null
      val notEmptyCell = notNullCell &&
        !url.getStringCellValue.trim.equals("") &&
        !rule.getStringCellValue.trim.equals("")
      notEmptyCell
    }
  }

  /**
   *
   *
   * SUPPORTED TEST ABSTRACTION - START
   *
   *
   */

  case object MatchContent extends TestType(8, 9)
  case object XpathContent extends TestType(10, 11)
  case object JsonPathContent extends TestType(12, 13)

  /**
   *
   *
   * SUPPORTED TEST ABSTRACTION - END
   *
   *
   */

  /**
   * Input test abstraction
   */
  case class InputTest( val testId : String,
                        val testType : TestType,
                        val description : String,
                        val url : String,
                        val rule : String)

  /**
   * This object encapsulate all information on test types handled into application
   */
  private class TestTypePreProcessor(supportedTestType : List[TestType]) {

    /**
     * Description column index
     */
    val descriptionColumn = 0

    /**
     * Monitoring column index
     */
    val monitoringColumn = 4

    /**
     * This list contains description from key row to be skipped
     */
    val desc2BeSkipped = List("Control", "Description")

    /**
     * Parse against the TestType handled and returns match
     *
     * @param row
     *
     * @return Option[TestType]
     */
    def identifyTestTypeFromRow(row: Row, requestedMonitoring : String = NO_MONITORING_): Option[TestType] = {

      var resp: Option[TestType] = None

      def descriptionCheck : Boolean = !desc2BeSkipped.contains(row.getCell(descriptionColumn).getStringCellValue)

      // it checks condition to apply monitoring filtering
      def monitoringCheck : Boolean = {

        // is it a request?
        def validRequestString : Boolean =  requestedMonitoring != null &&
                                  requestedMonitoring.length > 0 &&
                                  !requestedMonitoring.equals(NO_MONITORING_)

        // is monitor cell valid?
        def validMonitoringCell : Boolean = {
          val monitoringCell = row.getCell(monitoringColumn)
          monitoringCell != null && monitoringCell.getStringCellValue.length > 0
        }

        if ( validRequestString ) {// is request string valid?
          if ( validMonitoringCell ) {// execute real data check
            row.getCell(monitoringColumn).getStringCellValue.equals(requestedMonitoring)
          }
          else {
            false
          }
        }
        else {
          true
        }
      }

      if ( descriptionCheck && monitoringCheck ) {
        val result = supportedTestType.filter(_.validRow4TestType(row))

        resp = result.length match {
          case 0 => None
          case 1 => Some(result.head) // only one element
          case _ => {
            val message = s"The Row [${row.getCell(descriptionColumn).getStringCellValue}] matches with more than one test type!"
            logger.error(message)
            throw new IllegalArgumentException(message)
          }

        }
      }

      resp
    }

    /**
     * It creates a new GenericTest object with passed testId label and data contained into row
     *
     * @param testId
     * @param row
     *
     * @return it returns an exception when data into row does not match any template
     */
    def createNewGenericTest(testId: String, row: Row) : InputTest = {

      val testType = identifyTestTypeFromRow(row)

      val resp : InputTest = testType match {
        case Some(currTestType) => {

          def description : String = row.getCell(descriptionColumn).getStringCellValue

          def url : String = {
            var tempUrl = row.getCell(currTestType.urlColumn).getStringCellValue

            if ( !tempUrl.startsWith("http://") ) {
              tempUrl = "http://" + tempUrl;
            }

              tempUrl
          }

          def rule : String = row.getCell(currTestType.ruleColumn).getStringCellValue

          InputTest( testId, currTestType, description, url, rule)
        }
        case None => {
            val message = s"The Row [${row.getCell(descriptionColumn).getStringCellValue}] is not parsable as GenericTest!"
            logger.error(message)
            throw new IllegalArgumentException(message)
          }
      }

      resp
    }
  }

}
