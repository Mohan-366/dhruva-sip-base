package com.cisco.dsb.common.dto;

import com.cisco.wx2.dto.ErrorList;
import java.util.HashSet;
import javax.sip.InvalidArgumentException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TrustedSipSourcesTest {

  @DataProvider
  public Object[][] variousSources() {
    return new Object[][] {
      // { isValid, sources, expectedSize }
      // Positive test cases
      {true, "  ,   ,  ", 0},
      {true, "  , 127.0.0.1  , 192.168.1.1 ", 2},
      {true, "good.tld.com, , 192.168.1.1", 2},
      {true, "swift.corp, , 192.168.1.1", 2},
      {true, "CUCM.swift.corp, , 192.168.1.1", 2},
      {true, null, 0},
      // Negative test cases
      {false, " apple, 127.apple.0.1, ::1 ", 2},
      {false, "127.apple.0.1, 400, 192.168.1.1", 1}
    };
  }

  @Test(
      dataProvider = "variousSources",
      description =
          "provide trusted sip sources as CSV input, may include invalid inputs - hence validate them")
  public void validateSourcesProvidedAsCsvStyle(
      boolean isValid, String testValues, int expectedSize) {
    TrustedSipSources trustedSipSources = new TrustedSipSources(testValues);
    ErrorList errorList = trustedSipSources.validateSources();
    Assert.assertEquals(errorList.isEmpty(), isValid, "unexpected errorList: " + errorList);
    Assert.assertEquals(
        errorList.size(),
        trustedSipSources.size() - expectedSize,
        "unexpected errorList: " + errorList);
  }

  @Test(
      description = "provide trusted sip sources as hashset input & try to add more sources later",
      expectedExceptions = InvalidArgumentException.class)
  public void validateSourcesProvidedAsHashSet() throws InvalidArgumentException {
    // provide trustedSipSource input during initialisation (uses positive case)
    HashSet<String> testValues = new HashSet<>();
    testValues.add("apple");
    testValues.add("::1");
    TrustedSipSources trustedSipSources = new TrustedSipSources(testValues);
    Assert.assertEquals(trustedSipSources.getTrustedSipSources(), testValues);

    ErrorList errorList = trustedSipSources.validateSources();
    Assert.assertTrue(errorList.isEmpty());
    Assert.assertEquals(errorList.size(), 0);

    trustedSipSources.remove("apple");
    trustedSipSources.remove("::1");
    Assert.assertTrue(trustedSipSources.isEmpty());

    trustedSipSources.add("127.0.0.1"); // valid input can be inserted dynamically
    Assert.assertEquals(trustedSipSources.size(), 1);

    // invalid domain/ip address - so will not be added to the list,
    // just throws an invalidArgument exception
    trustedSipSources.add("127.apple.0.1");
  }

  @Test
  public void validateEquals() {
    EqualsVerifier.simple().forClass(TrustedSipSources.class).verify();
  }
}
