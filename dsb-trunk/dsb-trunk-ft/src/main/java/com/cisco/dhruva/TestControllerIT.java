package com.cisco.dhruva;

import com.cisco.dhruva.application.TestCaseRunner;
import com.cisco.dhruva.input.TestInput;
import com.cisco.dhruva.input.TestInput.TestCaseConfig;
import com.cisco.dhruva.util.TestListener;
import com.cisco.dhruva.validator.Validator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITest;
import org.testng.SkipException;
import org.testng.annotations.*;

@Listeners({IntegrationTestListener.class, TestListener.class})
public class TestControllerIT implements ITest {

  public static final Logger TEST_LOGGER = LoggerFactory.getLogger(TestControllerIT.class);
  private TestInput testCases;
  private ThreadLocal<String> testName = new ThreadLocal<>();

  @BeforeClass
  public void setUp() throws ParseException, IOException {
    readTestCasesJsonFile();
  }

  @BeforeMethod
  public void BeforeMethod(Method method, Object[] testData) {
    TestCaseConfig testCaseConfig = (TestCaseConfig) testData[0];
    testName.set(method.getName() + "_" + testCaseConfig.getId());
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
    TestCaseRunner testCaseRunner = new TestCaseRunner(testCaseConfig);
    testCaseRunner.prepareAndRunTest();

    TEST_LOGGER.info("Flow validation complete. Validating headers now");
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
    TEST_LOGGER.debug("Input JSON: \n" + jsonObject.toJSONString());
  }

  @Override
  public String getTestName() {
    return testName.get();
  }
}
