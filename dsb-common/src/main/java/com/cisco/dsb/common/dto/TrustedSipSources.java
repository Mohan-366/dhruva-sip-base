package com.cisco.dsb.common.dto;

import com.cisco.wx2.dto.ErrorInfo;
import com.cisco.wx2.dto.ErrorList;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Splitter;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import javax.sip.InvalidArgumentException;

public class TrustedSipSources {
  private LinkedHashSet<String> trustedSipSources = new LinkedHashSet<>();

  private static final Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();

  public static Iterable<String> splitSources(String values) {
    Iterable<String> result = new ArrayList<>();
    if (values != null) {
      result = splitter.split(values);
    }
    return result;
  }

  @JsonCreator
  public TrustedSipSources(
      @JsonProperty("trustedSipSources") LinkedHashSet<String> trustedSipSources) {
    setTrustedSipSources(trustedSipSources);
  }

  public TrustedSipSources() {}

  /**
   * Loads CSV values after trimming whitespace. Does not perform any other validation or
   * normalization on values
   *
   * @param values CSV list of sources
   */
  public TrustedSipSources(String values) {
    for (String source : splitSources(values)) {
      trustedSipSources.add(source);
    }
  }

  /**
   * Validates that each source looks like a valid IPv4, IPv6 or domain name
   *
   * @return ErrorList with entry for each invalid entry found
   */
  public ErrorList validateSources() {
    ErrorList errorList = new ErrorList();
    for (String source : trustedSipSources) {
      if (!InternetDomainName.isValid(source) && !InetAddresses.isInetAddress(source)) {
        errorList.add(new ErrorInfo(source));
      }
    }
    return errorList;
  }

  public LinkedHashSet<String> getTrustedSipSources() {
    return trustedSipSources;
  }

  public void setTrustedSipSources(LinkedHashSet<String> trustedSipSources) {
    this.trustedSipSources = trustedSipSources == null ? new LinkedHashSet<>() : trustedSipSources;
  }

  /**
   * Add a valid source
   *
   * @param String source to be added
   * @return <tt>true</tt> if this set did not already contain the specified element
   * @throws InvalidArgumentException if source is not a valid domain or ip address
   */
  public boolean add(String source) throws InvalidArgumentException {
    if (!InternetDomainName.isValid(source) && !InetAddresses.isInetAddress(source)) {
      throw new InvalidArgumentException(source);
    }
    return trustedSipSources.add(source);
  }

  /**
   * Remove a source
   *
   * @param String source to be removed
   * @return <tt>true</tt> if the value was removed
   */
  public boolean remove(String source) {
    return trustedSipSources.remove(source);
  }

  public int size() {
    return trustedSipSources.size();
  }

  public boolean isEmpty() {
    return trustedSipSources.isEmpty();
  }

  public String toString() {
    return trustedSipSources.toString();
  }
}
