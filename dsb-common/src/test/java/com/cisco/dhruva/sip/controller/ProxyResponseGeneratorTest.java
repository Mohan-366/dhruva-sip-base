package com.cisco.dhruva.sip.controller;

import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.proxy.Location;
import com.cisco.dhruva.sip.proxy.ProxyTransaction;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.util.SIPRequestBuilder;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.ArrayList;
import javax.sip.address.Address;
import javax.sip.message.Response;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProxyResponseGeneratorTest {

  @Mock ProxyTransaction proxyTransaction;

  @BeforeClass
  void init() {
    MockitoAnnotations.initMocks(this);
  }

  @BeforeMethod
  public void setup() {
    reset(proxyTransaction);
  }

  @Test(description = "test send internal server response for a invite request")
  public void testSendInternalServerResponse() throws Exception {

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    doNothing().when(proxyTransaction).respond();
    ProxyResponseGenerator.sendServerInternalErrorResponse(request, proxyTransaction);

    ArgumentCaptor<SIPResponse> argumentCaptor = ArgumentCaptor.forClass(SIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).respond(argumentCaptor.capture());

    SIPResponse response = argumentCaptor.getValue();

    Assert.assertNotNull(response);
    Assert.assertEquals(response.getStatusCode(), Response.SERVER_INTERNAL_ERROR);
    Assert.assertEquals(request.getCSeq().getSeqNumber(), response.getCSeq().getSeqNumber());
    Assert.assertEquals(response.getCSeq().getMethod(), request.getCSeq().getMethod());
    Assert.assertEquals(request.getCallId().getCallId(), response.getCallId().getCallId());
  }

  @Test(description = "test 404 response for invite request")
  public void testNotFoundResponse() throws Exception {
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    doNothing().when(proxyTransaction).respond();
    ProxyResponseGenerator.sendNotFoundResponse(request, proxyTransaction);

    ArgumentCaptor<SIPResponse> argumentCaptor = ArgumentCaptor.forClass(SIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).respond(argumentCaptor.capture());

    SIPResponse response = argumentCaptor.getValue();

    Assert.assertNotNull(response);
    Assert.assertEquals(response.getStatusCode(), Response.NOT_FOUND);
    Assert.assertEquals(request.getCSeq().getSeqNumber(), response.getCSeq().getSeqNumber());
    Assert.assertEquals(response.getCSeq().getMethod(), request.getCSeq().getMethod());
    Assert.assertEquals(request.getCallId().getCallId(), response.getCallId().getCallId());
  }

  @Test(description = "test create response")
  public void testCreateResponse() throws Exception {
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    SIPResponse response =
        ProxyResponseGenerator.createResponse(Response.SERVER_INTERNAL_ERROR, request);

    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getToTag());
    Assert.assertEquals(response.getStatusCode(), Response.SERVER_INTERNAL_ERROR);
    Assert.assertEquals(request.getCSeq().getSeqNumber(), response.getCSeq().getSeqNumber());
    Assert.assertEquals(response.getCSeq().getMethod(), request.getCSeq().getMethod());
    Assert.assertEquals(request.getCallId().getCallId(), response.getCallId().getCallId());

    response = ProxyResponseGenerator.createResponse(Response.MOVED_TEMPORARILY, request);

    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getToTag());
    Assert.assertEquals(response.getStatusCode(), Response.MOVED_TEMPORARILY);
    Assert.assertEquals(request.getCSeq().getSeqNumber(), response.getCSeq().getSeqNumber());
    Assert.assertEquals(response.getCSeq().getMethod(), request.getCSeq().getMethod());
    Assert.assertEquals(request.getCallId().getCallId(), response.getCallId().getCallId());

    request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.BYE));

    response = ProxyResponseGenerator.createResponse(Response.OK, request);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getToTag());
    Assert.assertEquals(response.getStatusCode(), Response.OK);
    Assert.assertEquals(request.getCSeq().getSeqNumber(), response.getCSeq().getSeqNumber());
    Assert.assertEquals(response.getCSeq().getMethod(), request.getCSeq().getMethod());
    Assert.assertEquals(request.getCallId().getCallId(), response.getCallId().getCallId());

    response = ProxyResponseGenerator.createResponse(Response.REQUEST_TIMEOUT, request);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getToTag());
    Assert.assertEquals(response.getStatusCode(), Response.REQUEST_TIMEOUT);
    Assert.assertEquals(request.getCSeq().getSeqNumber(), response.getCSeq().getSeqNumber());
    Assert.assertEquals(response.getCSeq().getMethod(), request.getCSeq().getMethod());
    Assert.assertEquals(request.getCallId().getCallId(), response.getCallId().getCallId());
  }

  @Test(description = "test send response")
  public void testSendResponse() throws Exception {
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    SIPResponse response =
        (SIPResponse)
            JainSipHelper.getMessageFactory().createResponse(Response.REQUEST_TIMEOUT, request);

    doNothing().when(proxyTransaction).respond();
    ProxyResponseGenerator.sendResponse(response, proxyTransaction);

    ArgumentCaptor<SIPResponse> argumentCaptor = ArgumentCaptor.forClass(SIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).respond(argumentCaptor.capture());

    SIPResponse responseTest = argumentCaptor.getValue();
    Assert.assertEquals(responseTest, response);
  }

  @Test(description = "test create 404 response")
  public void testCreate404Response() throws Exception {
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    SIPResponse response = ProxyResponseGenerator.createNotFoundResponse(request);

    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getToTag());
    Assert.assertEquals(response.getStatusCode(), Response.NOT_FOUND);
    Assert.assertEquals(request.getCSeq().getSeqNumber(), response.getCSeq().getSeqNumber());
    Assert.assertEquals(response.getCSeq().getMethod(), request.getCSeq().getMethod());
    Assert.assertEquals(request.getCallId().getCallId(), response.getCallId().getCallId());
  }

  @Test(description = "test create redirect response based on array of location objects")
  public void testCreateRedirectResponse() throws Exception {

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    ArrayList<Location> locations = new ArrayList<>(2);
    String requestUri1 = "<sip:" + "1.1.1.1" + ":" + 5061 + ">";
    String requestUri2 = "<sip:" + "2.2.2.2" + ":" + 5061 + ">";

    Address address1 = JainSipHelper.getAddressFactory().createAddress(requestUri1);
    Address address2 = JainSipHelper.getAddressFactory().createAddress(requestUri2);

    Location location1 = new Location(address1.getURI());
    Location location2 = new Location(address2.getURI());

    locations.add(location1);
    locations.add(location2);

    // Test with multiple locations
    SIPResponse response = ProxyResponseGenerator.createRedirectResponse(locations, request);
    Assert.assertNotNull(response);
    Assert.assertEquals(response.getStatusCode(), Response.MULTIPLE_CHOICES);

    // Test with single location, response code should be Moved temporarily
    locations.remove(0);
    response = ProxyResponseGenerator.createRedirectResponse(locations, request);
    Assert.assertNotNull(response);
    Assert.assertEquals(response.getStatusCode(), Response.MOVED_TEMPORARILY);
  }
}
