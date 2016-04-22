package it.vinmar

import java.io.FileOutputStream

import org.apache.poi.ss.usermodel.{Cell, CellStyle, Font, IndexedColors, Row, Sheet}
import org.apache.poi.xssf.usermodel.{XSSFFont, XSSFWorkbook}
import org.slf4j.LoggerFactory
import TestBookReader.InputTest
import TestResult.{Failed, Passed}
import TestSubStatus.{SeleniumError, SystemError, TimeoutError}

/**
 * General object that encapsulate result file writing
 */
object TestResultWriter {

  /**
   * Logger
   */
  private def logger = LoggerFactory.getLogger(this.getClass)

  /**
   * This trait is used to map column index into final sheet
   */
  sealed trait ColumnMapping { def index: Int }

  /**
   * Column section - START
   */

  case object TEST_ID extends ColumnMapping { val index = 0 }
  case object DESCRIPTION extends ColumnMapping { val index = 1 }
  case object TEST_URL extends ColumnMapping { val index = 2 }
  case object TEST_RULE extends ColumnMapping { val index = 3 }
  case object TEST_TYPE extends ColumnMapping { val index = 4 }
  case object EXEC_TIME extends ColumnMapping { val index = 5 }
  case object HTTP_ERR extends ColumnMapping { val index = 6 }

  /**
   * For formatting purpose
   */
  val latestColumn: ColumnMapping = HTTP_ERR

  /**
   * Column section - END
   */

  /**
   *
   */
  private val HEADER_STYLE: String = "header";
  private val DATA_STYLE_CENTER: String = "data_center";
  private val DATA_STYLE_LEFT: String = "data_left";

  /**
   * This method save a MS Excel file with collected results
   *
   * @param fileName
   * @param in
   * @param result
   */
  def generateDataSheetReport(fileName: String, in: List[InputTest], result: Seq[TestResult]): Unit = {

    logger.info("Start MS Excel writing")
    logger.info(s"File name -> ${fileName}")
    logger.info(s"There are ${in.size} input test.")
    logger.info(s"There are ${result.size} output result.")

    val wb: XSSFWorkbook = new XSSFWorkbook()

    val styles: Map[String, CellStyle] = {

      // black font big
      val blackFontBig: Font = wb.createFont
      blackFontBig.setBoldweight(XSSFFont.DEFAULT_FONT_SIZE)
      blackFontBig.setColor(IndexedColors.BLACK.getIndex());
      blackFontBig.setFontHeightInPoints(18)

      // black font small
      val blackFontSmall: Font = wb.createFont
      blackFontSmall.setColor(IndexedColors.BLACK.getIndex())
      blackFontSmall.setFontHeightInPoints(12)

      val styleHeader: CellStyle = wb.createCellStyle
      styleHeader.setAlignment(CellStyle.ALIGN_CENTER)
      styleHeader.setVerticalAlignment(CellStyle.VERTICAL_CENTER)
      styleHeader.setFillForegroundColor(IndexedColors.WHITE.getIndex())
      styleHeader.setFillPattern(CellStyle.SOLID_FOREGROUND)
      styleHeader.setFont(blackFontBig)

      val styleCenter: CellStyle = wb.createCellStyle
      styleCenter.setAlignment(CellStyle.ALIGN_CENTER)
      styleCenter.setVerticalAlignment(CellStyle.VERTICAL_CENTER)
      styleCenter.setFillForegroundColor(IndexedColors.WHITE.getIndex())
      styleCenter.setFillPattern(CellStyle.SOLID_FOREGROUND)
      styleCenter.setFont(blackFontSmall)

      val styleLeft: CellStyle = wb.createCellStyle
      styleLeft.setAlignment(CellStyle.ALIGN_LEFT)
      styleLeft.setVerticalAlignment(CellStyle.VERTICAL_CENTER)
      styleLeft.setFillForegroundColor(IndexedColors.WHITE.getIndex())
      styleLeft.setFillPattern(CellStyle.SOLID_FOREGROUND)
      styleLeft.setFont(blackFontSmall)

      val resp: Map[String, CellStyle] = Map(HEADER_STYLE -> styleHeader,
        DATA_STYLE_CENTER -> styleCenter,
        DATA_STYLE_LEFT -> styleLeft)

      resp
    }

    /**
     * It formats the new sheet
     *
     * @param sheet
     */
    def formatSheet(sheet: Sheet): Unit = {

      val headerStyle: CellStyle = styles.get(HEADER_STYLE).get

      sheet.setDisplayGridlines(false)
      sheet.setPrintGridlines(false)
      sheet.setFitToPage(true)
      sheet.setHorizontallyCenter(true)

      val headerRow: Row = sheet.createRow(0)
      var cell: Cell = headerRow.createCell(TEST_ID.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue("ID")
      cell.setCellStyle(headerStyle)
      cell = headerRow.createCell(DESCRIPTION.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue("Description")
      cell.setCellStyle(headerStyle)
      cell = headerRow.createCell(TEST_TYPE.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue("Test type")
      cell.setCellStyle(headerStyle)
      cell = headerRow.createCell(EXEC_TIME.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue("Load time (ms)")
      cell.setCellStyle(headerStyle)
      cell = headerRow.createCell(TEST_URL.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue("Test Url")
      cell.setCellStyle(headerStyle)
      cell = headerRow.createCell(HTTP_ERR.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue("Network resources")
      cell.setCellStyle(headerStyle)
      cell = headerRow.createCell(TEST_RULE.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue("Test rule")
      cell.setCellStyle(headerStyle)
    }

    /**
     * It add a single entry to result sheet
     *
     * @param sheet
     * @param in
     * @param result
     */
    def addSingleTest(sheet: Sheet, in: InputTest, result: TestResult): Unit = {

      val leftStyle: CellStyle = styles.get(DATA_STYLE_LEFT).get
      val centerStyle: CellStyle = styles.get(DATA_STYLE_CENTER).get

      val row = sheet.createRow(sheet.getLastRowNum + 1);

      var cell: Cell = row.createCell(TEST_ID.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue(in.testId)
      cell.setCellStyle(centerStyle)

      cell = row.createCell(DESCRIPTION.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue(in.description)
      cell.setCellStyle(leftStyle)

      cell = row.createCell(TEST_TYPE.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue(in.testType.toString)
      cell.setCellStyle(centerStyle)

      cell = row.createCell(EXEC_TIME.index, Cell.CELL_TYPE_STRING)
      val time : Long = result.loadTime
      if (time.equals(TimeoutError.code)){
        cell.setCellValue("Timeout!")
      }
      else if (time.equals(SeleniumError.code)){
        cell.setCellValue("Browser Error!")
      }
      else if (time.equals(SystemError.code)){
        cell.setCellValue("System Error!")
      }
      else {
        cell.setCellValue(time.toString)
      }
      cell.setCellStyle(centerStyle)

      cell = row.createCell(TEST_URL.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue(in.url)
      cell.setCellStyle(leftStyle)

      cell = row.createCell(HTTP_ERR.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue(if (result.errorNetResources) "Error!" else "")
      cell.setCellStyle(centerStyle)

      cell = row.createCell(TEST_RULE.index, Cell.CELL_TYPE_STRING)
      cell.setCellValue(in.rule)
      cell.setCellStyle(leftStyle)
    }

    val passedSheet: Sheet = wb.createSheet("Passed")
    val failedSheet: Sheet = wb.createSheet("Failed")

    formatSheet(passedSheet)
    formatSheet(failedSheet)

    for (singleResult <- (result sortBy (_.testId)) ) {
      val singleIn: Option[InputTest] = in.find((i: InputTest) => i.testId.equals(singleResult.testId))
      singleIn match {
        case Some(currentInputTest) => {
          val currSheet: Sheet = singleResult.result match {
            case Passed => passedSheet
            case Failed => failedSheet
          }
          addSingleTest( currSheet, currentInputTest, singleResult)
        }
        case None => throw new IllegalArgumentException
      }
    }

    for (index <- 0 to latestColumn.index) {
      passedSheet.autoSizeColumn(index);
      failedSheet.autoSizeColumn(index);
    }

    // Write the output to a file
    val out: FileOutputStream = new FileOutputStream(fileName);
    wb.write(out);
    out.close();
  }

}
