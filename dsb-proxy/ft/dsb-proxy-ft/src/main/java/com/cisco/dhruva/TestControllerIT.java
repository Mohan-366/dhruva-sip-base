package com.cisco.dhruva;

import static com.cisco.dhruva.util.FTLog.FT_LOGGER;

import com.cisco.dhruva.util.TestInput;
import com.cisco.dhruva.util.TestInput.TestCaseConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileReader;
import java.io.IOException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestControllerIT {
  private TestInput testCases;

  @BeforeClass
  public void setUp() throws ParseException, IOException {
    readTestCasesJsonFile();
  }

  @DataProvider(name = "testInput")
  public Object[] getTestInput() {
    return testCases.getTestCaseConfig();
  }

  @Test(dataProvider = "testInput")
  public void testDSB(Object object) throws Exception {
    TestCaseConfig testCaseConfig = (TestCaseConfig) object;
    if (testCaseConfig.isSkipTest()) {
      throw new SkipException("Skipping test: " + testCaseConfig.getDescription());
    }
    FT_LOGGER.info("Executing FT: {}" + "", testCaseConfig.getDescription());
    TestCaseRunner testCaseRunner = new TestCaseRunner(testCaseConfig);
    testCaseRunner.prepareAndRunTest();

    FT_LOGGER.info("Flow validation complete. Validating headers now");
    Validator validator = new Validator(testCaseRunner.getUac(), testCaseRunner.getUasList());
    validator.validate();
  }

  private void readTestCasesJsonFile() throws IOException, ParseException {
    String testFilePath =
        TestControllerIT.class.getClassLoader().getResource("testcases.json").getPath();
    ObjectMapper mapper = new ObjectMapper();
    JSONParser parser = new JSONParser();
    Object object = parser.parse(new FileReader(testFilePath));
    JSONObject jsonObject = (JSONObject) object;
    testCases = mapper.readValue(jsonObject.toJSONString(), TestInput.class);
    FT_LOGGER.info("Input JSON: \n" + jsonObject.toJSONString());
  }
}
