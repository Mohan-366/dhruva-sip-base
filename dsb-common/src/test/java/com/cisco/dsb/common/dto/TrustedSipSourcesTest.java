package com.cisco.dsb.common.dto;

import com.cisco.wx2.dto.ErrorInfo;
import com.cisco.wx2.dto.ErrorList;
import java.util.HashSet;
import java.util.Objects;
import javax.sip.InvalidArgumentException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TrustedSipSourcesTest {
  @Test
  void testSplitSources() {
    // TODO: This test is incomplete.

    TrustedSipSources.splitSources("42").iterator();
  }

  @Test
  void testConstructor() {
    Assert.assertEquals("[]", (new TrustedSipSources()).toString());
    Assert.assertEquals(1, (new TrustedSipSources("42")).getTrustedSipSources().size());
    Assert.assertTrue((new TrustedSipSources((String) null)).getTrustedSipSources().isEmpty());
    Assert.assertTrue((new TrustedSipSources(",")).getTrustedSipSources().isEmpty());
    Assert.assertTrue((new TrustedSipSources(new HashSet<>())).getTrustedSipSources().isEmpty());
  }

  @Test
  void testValidateSources() {
    ErrorList actualValidateSourcesResult = (new TrustedSipSources("42")).validateSources();
    Assert.assertEquals(1, actualValidateSourcesResult.size());
    ErrorInfo getResult = actualValidateSourcesResult.get(0);
    Assert.assertEquals("42", getResult.getDescription());
    Assert.assertNull(getResult.getErrorCode());
  }

  @Test
  void testValidateSources2() {
    Assert.assertTrue((new TrustedSipSources("Values")).validateSources().isEmpty());
  }

  @Test
  void testValidateSources3() {
    Assert.assertTrue(
        (new TrustedSipSources("com.cisco.dsb.common.dto.TrustedSipSources"))
            .validateSources()
            .isEmpty());
  }

  @Test
  void testValidateSources4() {
    ErrorList actualValidateSourcesResult = (new TrustedSipSources("42Values")).validateSources();
    Assert.assertEquals(1, actualValidateSourcesResult.size());
    ErrorInfo getResult = actualValidateSourcesResult.get(0);
    Assert.assertEquals("42Values", getResult.getDescription());
    Assert.assertNull(getResult.getErrorCode());
  }

  @Test
  void testValidateSources5() {
    HashSet<String> stringSet = new HashSet<>();
    stringSet.add("foo");
    Assert.assertTrue((new TrustedSipSources(stringSet)).validateSources().isEmpty());
  }

  @Test
  void testValidateSources6() {
    HashSet<String> stringSet = new HashSet<>();
    stringSet.add("");
    ErrorList actualValidateSourcesResult = (new TrustedSipSources(stringSet)).validateSources();
    Assert.assertEquals(1, actualValidateSourcesResult.size());
    ErrorInfo getResult = actualValidateSourcesResult.get(0);
    Assert.assertEquals("", getResult.getDescription());
    Assert.assertNull(getResult.getErrorCode());
  }

  @Test
  void testValidateSources7() throws InvalidArgumentException {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    trustedSipSources.add(
        "com.cisco.dsb.common.dto.TrustedSipSourcescom.cisco.dsb.common.dto.TrustedSipSources");
    ErrorList actualValidateSourcesResult = trustedSipSources.validateSources();
    Assert.assertEquals(1, actualValidateSourcesResult.size());
    ErrorInfo getResult = actualValidateSourcesResult.get(0);
    Assert.assertEquals("42", getResult.getDescription());
    Assert.assertNull(getResult.getErrorCode());
  }

  @Test
  void testSetTrustedSipSources() {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    trustedSipSources.setTrustedSipSources(new HashSet<>());
    Assert.assertEquals("[]", trustedSipSources.toString());
  }

  @Test
  void testAdd() throws InvalidArgumentException {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    Assert.assertTrue(trustedSipSources.add("Source"));
    Assert.assertEquals(2, trustedSipSources.getTrustedSipSources().size());
  }

  @Test
  void testAdd2() throws InvalidArgumentException {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    trustedSipSources.add("Source");
    Assert.assertFalse(trustedSipSources.add("Source"));
    Assert.assertEquals(2, trustedSipSources.getTrustedSipSources().size());
  }

  @Test
  void testAdd3() throws InvalidArgumentException {
    Assert.assertThrows(
        InvalidArgumentException.class, () -> (new TrustedSipSources("42")).add(","));
  }

  @Test
  void testAdd4() throws InvalidArgumentException {
    Assert.assertThrows(
        InvalidArgumentException.class, () -> (new TrustedSipSources("42")).add("42"));
  }

  @Test
  void testAdd5() throws InvalidArgumentException {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    Assert.assertTrue(trustedSipSources.add("com.cisco.dsb.common.dto.TrustedSipSources"));
    Assert.assertEquals(2, trustedSipSources.getTrustedSipSources().size());
  }

  @Test
  void testAdd6() throws InvalidArgumentException {
    Assert.assertThrows(
        InvalidArgumentException.class, () -> (new TrustedSipSources("42")).add(""));
  }

  @Test
  void testRemove() {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    Assert.assertFalse(trustedSipSources.remove("Source"));
    Assert.assertEquals(1, trustedSipSources.getTrustedSipSources().size());
  }

  @Test
  void testRemove2() throws InvalidArgumentException {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    trustedSipSources.add("Source");
    Assert.assertTrue(trustedSipSources.remove("Source"));
    Assert.assertEquals(1, trustedSipSources.getTrustedSipSources().size());
  }

  @Test
  void testSize() {
    Assert.assertEquals(1, (new TrustedSipSources("42")).size());
  }

  @Test
  void testIsEmpty() {
    Assert.assertFalse((new TrustedSipSources("42")).isEmpty());
    Assert.assertTrue((new TrustedSipSources(",")).isEmpty());
  }

  @Test
  void testEquals() {
    Assert.assertFalse((new TrustedSipSources("42")).equals(null));
    Assert.assertFalse((new TrustedSipSources("42")).equals("Different type to TrustedSipSources"));
  }

  @Test
  void testEquals2() {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    Assert.assertTrue(trustedSipSources.equals(trustedSipSources));
    int expectedHashCodeResult = trustedSipSources.hashCode();
    Assert.assertEquals(expectedHashCodeResult, trustedSipSources.hashCode());
  }

  @Test
  void testEquals3() {
    TrustedSipSources trustedSipSources = new TrustedSipSources("42");
    TrustedSipSources trustedSipSources1 = new TrustedSipSources("42");
    Assert.assertTrue(trustedSipSources.equals(trustedSipSources1));
    int notExpectedHashCodeResult = trustedSipSources.hashCode();
    Assert.assertFalse(Objects.equals(notExpectedHashCodeResult, trustedSipSources1.hashCode()));
  }

  @Test
  void testEquals4() {
    TrustedSipSources trustedSipSources = new TrustedSipSources("Values");
    Assert.assertFalse(trustedSipSources.equals(new TrustedSipSources("42")));
  }
}
