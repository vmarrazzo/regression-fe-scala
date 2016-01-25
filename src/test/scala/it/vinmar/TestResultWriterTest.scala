package it.vinmar

import java.io.FileInputStream
import java.nio.file.{Files, Path, Paths}

import it.vinmar.TestBookReader.InputTest
import org.apache.poi.ss.usermodel.{Row, Sheet, WorkbookFactory, Workbook}

import org.scalatest._
import org.scalatest.Matchers._

import it.vinmar.TestResult._
import it.vinmar.TestResult.Failed

import it.vinmar.TestSubStatus._

import it.vinmar.TestBookReader._
import it.vinmar.TestResultWriter._

class TestResultWriterTest extends FlatSpec with BeforeAndAfterAll with MustMatchers {

  // filename for test
  val file2Save = "./target/sample.xlsx"

  val COL_ID = 0
  val COL_DE = 1
  val COL_UR = 2
  val COL_RU = 3
  val COL_TY = 4
  val COL_LO = 5
  val COL_NE = 6

  override def beforeAll {

    val path : Path = Paths.get(file2Save);
    Files.deleteIfExists(path)
  }

  it should "be able to create an excel report from data" in {

    val in : List[InputTest] =  InputTest( "Test_0001", XpathContent, "First Test", "http://first.test.org", "funny one rules!") ::
                                InputTest( "Test_0002", MatchContent, "Second Test", "http://second.test.org", "funny second rules!") ::
                                InputTest( "Test_0003", XpathContent, "Third Test", "http://third.test.org", "funny third rules!") ::
                                InputTest( "Test_0004", MatchContent, "Fourth Test", "http://fourth.test.org", "funny fourth rules!") ::
                                InputTest( "Test_0005", MatchContent, "Fifth Test", "http://fifth.test.org", "funny fifth rules!") ::
                                Nil

    val noErrorMap: Map[String, Int] = Map[String, Int](  "correct1" -> 200,
                                                          "correct2" -> 201,
                                                          "correct3" -> 300)


    val result : List[TestResult] = new TestResult("Test_0001", Passed, 12345, Some(noErrorMap)) ::
                                    new TestResult("Test_0002", Passed,  6345, Some(noErrorMap + ("incorrectClient" -> 400))) ::
                                    new TestResult("Test_0003", Failed, 14345, Some(noErrorMap)) ::
                                    new TestResult("Test_0004", Failed, 17845, Some(noErrorMap + ("incorrectServer" -> 500))) ::
                                    new TestResult("Test_0005", Failed, TimeoutError.code, Some(noErrorMap + ("incorrectServer" -> 500))) ::
                                    Nil

    val resultFile = new java.io.File(file2Save)

    resultFile should not (exist)

    generateDataSheetReport( file2Save, in, result)

    resultFile should (exist)

    val wb = WorkbookFactory.create(new FileInputStream(resultFile))

    wb.getNumberOfSheets shouldBe 2

    val passed : Sheet = wb.getSheet("Passed")
    val failed : Sheet = wb.getSheet("Failed")

    passed should not be (null)
    failed should not be (null)

    def checkDataRow(   row : Row,
                        expTestId : String,
                        expDescr : String,
                        expUrl : String,
                        expRule : String,
                        expTestType : String,
                        expLoadTime : String,
                        expNetRes : String ) : Unit = {

      row.getCell(COL_ID).getStringCellValue should be (expTestId)
      row.getCell(COL_DE).getStringCellValue should be (expDescr)
      row.getCell(COL_UR).getStringCellValue should be (expUrl)
      row.getCell(COL_RU).getStringCellValue should be (expRule)
      row.getCell(COL_TY).getStringCellValue should be (expTestType)
      row.getCell(COL_LO).getStringCellValue should be (expLoadTime)
      row.getCell(COL_NE).getStringCellValue should be (expNetRes)
    }

    def checkHeaderRow(row : Row) : Unit = {

        checkDataRow( row,
                      "ID",
                      "Description",
                      "Test Url",
                      "Test rule",
                      "Test type",
                      "Load time (ms)",
                      "Network resources"
        )
    }

    checkHeaderRow(passed.getRow(0))
    checkHeaderRow(failed.getRow(0))

    checkDataRow( passed.getRow(1), "Test_0001", "First Test", "http://first.test.org", "funny one rules!", "XpathContent", "12345", "")
    checkDataRow( passed.getRow(2), "Test_0002", "Second Test", "http://second.test.org", "funny second rules!", "MatchContent", "6345", "Error!")
    checkDataRow( failed.getRow(1), "Test_0003", "Third Test", "http://third.test.org", "funny third rules!", "XpathContent", "14345", "")
    checkDataRow( failed.getRow(2), "Test_0004", "Fourth Test", "http://fourth.test.org", "funny fourth rules!", "MatchContent", "17845", "Error!")
  }

}
