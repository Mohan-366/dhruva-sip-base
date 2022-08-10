package com.cisco.dhruva.antares.integration.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

public class IntegrationTestListener extends TestListenerAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTestListener.class);

  public static final Set<String> COMPLETED_TESTS = Collections.synchronizedSet(new HashSet<>());
  public static final Set<String> FAILED_TESTS = Collections.synchronizedSet(new HashSet<>());
  public static final Set<String> SKIPPED_TESTS = Collections.synchronizedSet(new HashSet<>());
  public static final Set<String> PASSED_TESTS = Collections.synchronizedSet(new HashSet<>());
  public static final Set<String> ALL_TESTS = Collections.synchronizedSet(new HashSet<>());

  private static final Object lock = new Object();

  private static final PeriodFormatter dateFormatter =
      new PeriodFormatterBuilder()
          .appendMinutes()
          .appendSuffix("m")
          .appendSeconds()
          .appendSuffix("s")
          .appendMillis()
          .appendSuffix("ms")
          .toFormatter();

  private enum TestState {
    Failed,
    Skipped,
    Started,
    Success
  }

  @Override
  public void onConfigurationSuccess(ITestResult itr) {
    super.onConfigurationSuccess(itr);
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult tr) {
    onTestFailure(tr);
  }

  @Override
  public void onTestStart(ITestResult result) {
    synchronized (lock) {
      super.onTestStart(result);
      logAndTrackTestResult(result, TestState.Started);
    }
  }

  @Override
  public void onTestSuccess(ITestResult tr) {
    synchronized (lock) {
      super.onTestSuccess(tr);
      logAndTrackTestResult(tr, TestState.Success);
    }
  }

  @Override
  public void onTestFailure(ITestResult tr) {
    synchronized (lock) {
      super.onTestFailure(tr);
      logAndTrackTestResult(tr, TestState.Failed);
    }
  }

  @Override
  public void onTestSkipped(ITestResult tr) {
    synchronized (lock) {
      logAndTrackTestResult(tr, TestState.Skipped);
    }
  }

  protected void logTotals() {
    LOGGER.info(
        "Total tests: {}, completed: {}, passed: {}, failed: {}, skipped: {}",
        ALL_TESTS.size(),
        COMPLETED_TESTS.size(),
        PASSED_TESTS.size(),
        FAILED_TESTS.size(),
        SKIPPED_TESTS.size());
    logJvmMemoryStats();
  }

  static void logJvmMemoryStats() {
    int mb = 1024 * 1024;
    Runtime runtime = Runtime.getRuntime();
    LOGGER.info(
        "JVM Memory Stats Used: {}MB, Free: {}MB, Total: {}MB, Max: {}MB",
        (runtime.totalMemory() - runtime.freeMemory()) / mb,
        runtime.freeMemory() / mb,
        runtime.totalMemory() / mb,
        runtime.maxMemory() / mb);
  }

  private void logAndTrackTestResult(ITestResult testResult, TestState state) {
    final String testName =
        testResult.getMethod().getTestClass().getRealClass().getName() + "." + testResult.getName();

    switch (state) {
      case Started:
        ALL_TESTS.add(testName);

        final String testClass = testResult.getMethod().getTestClass().getRealClass().getName();
        LOGGER.info("Running tests in class: {}", testClass);

        logTestResult(testResult, TestState.Started);
        break;

      case Success:
        PASSED_TESTS.add(testName);

        // Don't add to the completed list if already there (because maybe it failed once before)
        if (!COMPLETED_TESTS.contains(testName)) {
          COMPLETED_TESTS.add(testName);
        }

        // remove from the failed list
        if (FAILED_TESTS.contains(testName)) {
          FAILED_TESTS.remove(testName);

          // if the test has failed before but is passing now, add it to the retried test list
        }

        logTestResult(testResult, TestState.Success);
        break;

      case Failed:
        // reportMdcValues(testResult);

        // if the test has retries available, don't keep track of the failed result
        // this will avoid redundant confusing results

        COMPLETED_TESTS.add(testName);
        FAILED_TESTS.add(testName);

        logTestResult(testResult, TestState.Failed);
        break;

        // Skipped tests
      default:
        // if the test failed but has retries available, don't log it
        // as a skipped test but instead record it as "retried"

        super.onTestSkipped(testResult);
        logTestResult(testResult, TestState.Skipped);

        if (!SKIPPED_TESTS.contains(testName)) {
          SKIPPED_TESTS.add(testName);
        }

        break;
    }

    logTotals();
  }

  private void logTestResult(ITestResult testResult, TestState state) {
    final String testClass = testResult.getMethod().getTestClass().getRealClass().getSimpleName();
    final String testName = testResult.getName();
    final String testFullName = testClass + " " + testName;
    final StringBuilder buffer = new StringBuilder();
    buffer
        .append("Test ")
        .append(state.toString().toLowerCase())
        .append(": ")
        .append(testFullName)
        .append(" ");

    // Don't need duration when we start or skip a test
    if (!(state.equals(TestState.Skipped) || state.equals(TestState.Started))) {
      buffer.append("Took: ").append(printDuration(testResult)).append(" ");
    }

    // Skipped tests don't need a tracking id
    Throwable reasonSkipped = testResult.getThrowable();
    if (reasonSkipped != null) {
      buffer.append("Exception: ");
      LOGGER.info(buffer.toString(), reasonSkipped);
    } else {
      LOGGER.info(buffer.toString());
    }
  }

  private String printDuration(ITestResult tr) {
    Duration duration = new Duration(tr.getEndMillis() - tr.getStartMillis());
    return dateFormatter.print(duration.toPeriod());
  }

  private void cleanTestResults(ITestContext testContext) {
    for (ITestNGMethod method : testContext.getAllTestMethods()) {
      // if this test was run more thant once, remove the last failure result
      if (method.getCurrentInvocationCount() > 1) {
        while (testContext.getFailedTests().getResults(method).size() > 1) {
          testContext.getFailedTests().removeResult(method);
        }

        // if the test eventually passed, remove the last possible remaining failure
        if (testContext.getPassedTests().getResults(method).size() == 1) {
          testContext.getFailedTests().removeResult(method);
        }

        // if we have a failure or a pass, remove the skipped result
        if (testContext.getFailedTests().getResults(method).size() > 0
            || testContext.getPassedTests().getResults(method).size() > 0) {
          testContext.getSkippedTests().removeResult(method);
        }
      }
    }
  }

  @Override
  public void onFinish(ITestContext testContext) {
    super.onFinish(testContext);

    LOGGER.info("onFinish: {}", testContext.getName());

    cleanTestResults(testContext);

    if (IntegrationTestListener.FAILED_TESTS.size() > 0) {
      LOGGER.error("The following tests failed:");
      IntegrationTestListener.FAILED_TESTS.forEach(LOGGER::error);
    }

    if (IntegrationTestListener.SKIPPED_TESTS.size() > 0) {
      LOGGER.error("The following tests were skipped:");
      IntegrationTestListener.SKIPPED_TESTS.forEach(LOGGER::error);
    }

    LOGGER.info("--------------------------------------------------------------");
    LOGGER.info("Tests Summary");
    logTotals();
    LOGGER.info("--------------------------------------------------------------");
  }
}
