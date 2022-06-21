package com.cisco.dsb.proxy.controller;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.util.SipTag;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
import gov.nist.javax.sip.Utils;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.List;
import javax.sip.header.ContactHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.NonNull;

/*
 * This class enapsulates the funtionality of several commonly sent responses.
 */
@CustomLog
public abstract class ProxyResponseGenerator {

  public static final String NL = System.getProperty("line.separator");

  /*  public static SIPResponse createRedirectResponse(ArrayList locations, SIPRequest request)
      throws Exception {
    Log.debug("Entering createRedirectResponse()");
    ContactList contactHeaders = new ContactList();

    int size = locations.size();

    if (size > 0) {
      if (size == 1) {
        Destination destination = (Destination) locations.get(0);
        URI uri = destination.getUri();
        Address address = JainSipHelper.getAddressFactory().createAddress(uri.toString());
        ContactHeader contactHeader = JainSipHelper.getHeaderFactory().createContactHeader(address);
        contactHeader.setQValue(destination.getQValue());
        contactHeaders.add((Contact) contactHeader);
      } else {
        for (Object o : locations) {
          Destination destination = (Destination) o;
          URI uri = destination.getUri();
          Address address = JainSipHelper.getAddressFactory().createAddress(uri.toString());
          ContactHeader contactHeader =
              JainSipHelper.getHeaderFactory().createContactHeader(address);
          contactHeader.setQValue(destination.getQValue());
          contactHeaders.add((Contact) contactHeader);
        }
      }
    }
    Log.debug("Leaving createRedirectResponse()");
    return createRedirectResponse(contactHeaders, request);
  }*/

  /**
   * This is a utility method that sends a redirect Response depending on the number of contact
   * elements in the contact header. If there are multiple contacts, then a 300 will be generated,
   * otherwise a 302 will be generate.
   *
   * @param contactHeaders <CODE>DsSipContactHeader</CODE> object containg the contact elemnts.
   * @param request The request original request that this response messeage is responding to.
   * @param trans The proxy transaction that will be used to send the response.
   */
  public static void sendRedirectResponse(
      ContactList contactHeaders,
      /*DsSipContactHeader contactHeader,*/
      SIPRequest request,
      ProxyTransaction trans)
      throws Exception {
    logger.debug("Entering sendRedirectResponse()");
    SIPResponse response = createRedirectResponse(contactHeaders, request);

    // send the response.
    trans.respond(response);
    logger.debug("Leaving sendRedirectResponse()");
  }

  public static SIPResponse createRedirectResponse(
      ContactList contactHeaders,
      /*DsSipContactHeader contactHeader,*/
      SIPRequest request)
      throws DhruvaException, ParseException {
    logger.debug("Entering createRedirectResponse()");
    SIPResponse response = null;
    // added by BJ
    ContactHeader contactHeader = (ContactHeader) contactHeaders.getFirst();
    // check to see if the contactHeader contains more than
    // one element. If yes create redirect header with
    // response code-MULTIPLE_CHOICES ( # 300 )
    //
    // NOTE: Ideally contactHeader should never be null.
    if ((contactHeader != null) && (contactHeaders.size() > 1)) {
      response =
          (SIPResponse)
              JainSipHelper.getMessageFactory().createResponse(Response.MULTIPLE_CHOICES, request);

    } else {
      // if one or less contact create redirect header with response
      // type-MOVED_TEMPORARILY ( #302)
      response =
          (SIPResponse)
              JainSipHelper.getMessageFactory().createResponse(Response.MOVED_TEMPORARILY, request);
    }
    // DSB TODO
    // SIPResponse.getReasonPhrase(statusCode);
    // response.setApp();

    logger.debug("Created {} response", response.getStatusCode());

    ToHeader toHeader = response.getToHeader();
    if (toHeader.getTag() == null) {
      toHeader.setTag(Utils.getInstance().generateTag());
    }

    // add redirect header to the response.

    List<Contact> contactHeaderList = contactHeaders.getHeaderList();
    for (Contact contact : contactHeaderList) {
      response.addHeader(contact);
    }

    logger.debug("Leaving createRedirectResponse()");
    return response;
  }

  /**
   * Utility function that sends Internal Server Error response
   *
   * @param request The request original request that this response messeage is responding to.
   * @param trans The stateful proxy transaction that will be used to send the response.
   */
  public static void sendServerInternalErrorResponse(SIPRequest request, ProxyTransaction trans)
      throws DhruvaException, ParseException {
    logger.debug("Entering sendServerInternalErrorResponse()");

    SIPResponse response =
        (SIPResponse)
            JainSipHelper.getMessageFactory()
                .createResponse(Response.SERVER_INTERNAL_ERROR, request);

    ToHeader toHeader = response.getToHeader();
    if (toHeader.getTag() == null) {
      toHeader.setTag(SipTag.generateTag());
    }

    trans.respond(response);
    logger.debug("Leaving sendServerInternalErrorResponse()");
  }

  public static SIPResponse createNotFoundResponse(SIPRequest request)
      throws DhruvaException, ParseException {
    logger.debug("Entering createNotFoundResponse()");
    SIPResponse response =
        (SIPResponse) JainSipHelper.getMessageFactory().createResponse(Response.NOT_FOUND, request);

    ToHeader toHeader = response.getToHeader();
    if (toHeader.getTag() == null) {
      toHeader.setTag(SipTag.generateTag());
    }

    logger.debug("Leaving createNotFoundResponse()");

    return response;
  }

  /**
   * Utility function that sends 404 responses. Note, you must use a stateful transaction to send
   * the response.
   *
   * @param request The request original request that this response message is responding to.
   * @param trans The stateful proxy transaction that will be used to send the response.
   */
  public static void sendNotFoundResponse(SIPRequest request, ProxyTransaction trans)
      throws DhruvaException, ParseException {
    logger.debug("Entering sendNotFoundResponse()");
    trans.respond(createNotFoundResponse(request));
    logger.debug("Leaving sendNotFoundResponse()");
  }

  public static void sendTryingResponse(SIPRequest request, ProxyTransaction trans)
      throws ParseException {
    logger.debug("Entering sendTryingResponse()");
    SIPResponse response =
        (SIPResponse) JainSipHelper.getMessageFactory().createResponse(Response.TRYING, request);

    trans.respond(response);
  }

  public static void sendResponse(@NonNull SIPResponse response, @NonNull ProxyTransaction trans) {
    logger.debug("Entering sendResponse()");

    if (trans != null) {
      trans.respond(response);
      logger.debug("Sent response:" + NL + response);
    } else {
      logger.warn("ProxyTransaction was null!");
    }
  }

  /** Creates a response of the given type, and tags the To header. */
  public static SIPResponse createResponse(int responseCode, SIPRequest request)
      throws DhruvaException, ParseException {
    logger.debug("Entering createResponse()");
    SIPResponse response =
        (SIPResponse) JainSipHelper.getMessageFactory().createResponse(responseCode, request);

    String tag = response.getToTag();
    logger.debug("To tag is " + tag);
    if (tag == null) {
      logger.debug("Generating To tag");
      ToHeader toHeader = response.getToHeader();
      if (toHeader != null) toHeader.setTag(SipTag.generateTag());
    }
    return response;
  }
}
