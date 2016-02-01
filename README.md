# regression-fe-scala

regression-fe-scala is a quality assurance tool with goal to provide test automation facilities for web front-end regression. The basic idea is to catalog entire regression suite as MS Excel sheet and, after suite execution, provides as MS Excel sheet with results.
 
Each input test case is composed by (url to be tested, component to be checked). For each test execution will :<br>
1. load requested url<br>
2. ensure that DOM is correctly loaded<br>
3. verify with component presence<br>

The generic test is quite simple and can be used ot verify presence of described element into loaded web page. More detailed is regression suite and more test coverage can be reached about a complex web page (e.g. an aggregator of differente source).

The regression suite tends to grow with time increasing the included cases. To provide an acceptable time of execution this application executes in parallel test suite on difference browser process and aggregate final results.

### How to use this application

1. Java 8 on your environment
2. Firefox installed on your environment
3. Open [release page](https://github.com/vmarrazzo/regression-fe-scala/releases) 
4. Download regression-fe-scala-assembly-X.X.X.jar
5. Downalod SampleTestBook.xlsx
6. Launch command
```
java -jar regression-fe-scala-assembly-0.1.0.jar --testfile SampleTestBook.xlsx --sheetname CustomerSheet
```
After application end will be available a report file FE-Regress_YYYYMMDD_HHMMSS.xlsx with test report

###### Extending regression suite

Adding test to existring regression can be donw through new rows that describes testing url and component rule. There are two possible rule choices:<br>
a. Match content, it verify that loaded page contains a piece of text into page source<br>
b. XPath, it verify that described component with [XPath](http://toolsqa.com/selenium-webdriver/choosing-effective-xpath/) is present<br><br>

Each kind of rule is placed into a differente column for a row. (Into a same row cannot be Match content and XPath!!!!)

| Rule type | Url column | Rule column |
| :------------|:---------------| :-----|
| Match content | I | J |
| XPath | K | L |

Into the column A will be placed test case description for final reports.

### Disclaimer

This program is free software. It comes without any warranty, to the extent permitted by applicable law. You can redistribute it and/or modify it under the terms of the GPLv3. Use this at your own risk. Author is not responsible for anything you do with it, or for any damage that comes out of using it.
