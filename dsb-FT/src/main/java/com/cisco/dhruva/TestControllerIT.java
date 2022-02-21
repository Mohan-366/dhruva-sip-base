package com.cisco.dhruva;

import com.cisco.dhruva.util.TestInput;
import com.cisco.dhruva.util.TestInput.TestCaseConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestControllerIT {
  private TestInput testCases;

  private void readTestcasesJsonFile()
      throws FileNotFoundException, IOException, ParseException,
          org.json.simple.parser.ParseException {
    String testFilePath = SplitIT.class.getClassLoader().getResource("testcases.json").getPath();
    ObjectMapper mapper = new ObjectMapper();
    JSONParser parser = new JSONParser();
    Object object = parser.parse(new FileReader(testFilePath));
    JSONObject jsonObject = (JSONObject) object;
    testCases = (TestInput) mapper.readValue(jsonObject.toJSONString(), TestInput.class);

    Map<String, Object> suiteSpecificCpProperties = testCases.getCpProperties();
    System.out.println("Input JSON: \n" + jsonObject.toJSONString());
  }

  @BeforeClass
  public void setUp() throws ParseException, org.json.simple.parser.ParseException, IOException {
    readTestcasesJsonFile();
  }

  @DataProvider(name = "testInput")
  public Object[] getTestInput() {
    return testCases.getConfig();
  }

  @Test(dataProvider = "testInput")
  public void testDSB(Object object) throws Exception {
    TestCaseConfig testCaseConfig = (TestCaseConfig) object;
    System.out.println(testCaseConfig.getDescription());
    TestCaseRunner testCaseRunner = new TestCaseRunner(testCaseConfig);
    testCaseRunner.prepareAndRunTest();
    Thread.sleep(30000);
  }
}
