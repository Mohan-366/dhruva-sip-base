package com.cisco.dhruva.sip.controller;


import com.cisco.dhruva.sip.proxy.ControllerInterface;
import com.cisco.dhruva.sip.proxy.Location;
import com.cisco.dhruva.sip.proxy.ProxyTransaction;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.stack.util.SipTag;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.Utils;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import gov.nist.javax.sip.header.SIPHeaderList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.address.Address;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/*
 * This class enapsulates the funtionality of several commonly sent responses.
 */

public abstract class ProxyResponseGenerator {

    /** our log object * */
    private static final Logger Log = DhruvaLoggerFactory.getLogger(ProxyResponseGenerator.class);

    public static final String NL = System.getProperty("line.separator");

    public static SIPResponse createRedirectResponse(ArrayList locations, SIPRequest request)
            throws Exception {
        Log.debug("Entering createRedirectResponse()");
        ContactList contactHeaders = new ContactList();

        int size = locations.size();

        if (size > 0) {
            if (size == 1) {
                Location location = (Location) locations.get(0);
                URI uri = location.getURI();
                ContactHeader contactHeader = JainSipHelper.getHeaderFactory().createContactHeader((Address) uri);
                contactHeader.setQValue(location.getQValue());
                contactHeaders.add((Contact) contactHeader);
            } else {
                for (Object o : locations) {
                    Location location = (Location) o;
                    URI uri = location.getURI();
                    ContactHeader contactHeader = JainSipHelper.getHeaderFactory().createContactHeader((Address) uri);
                    contactHeader.setQValue(location.getQValue());
                    contactHeaders.add((Contact) contactHeader);
                }
            }
        }
        Log.debug("Leaving createRedirectResponse()");
        return createRedirectResponse(contactHeaders, request);
    }

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
        Log.debug("Entering sendRedirectResponse()");
        SIPResponse response = createRedirectResponse(contactHeaders, request);

        // send the response.
        trans.respond(response);
        Log.debug("Leaving sendRedirectResponse()");
    }

    public static SIPResponse createRedirectResponse(
            ContactList contactHeaders,
            /*DsSipContactHeader contactHeader,*/
            SIPRequest request)
            throws DhruvaException, ParseException {
        Log.debug("Entering createRedirectResponse()");
        SIPResponse response = null;
        // added by BJ
        ContactList contactHeader = (ContactList) contactHeaders.getFirst();
        // check to see if the contactHeader contains more than
        // one element. If yes create redirect header with
        // response code-MULTIPLE_CHOICES ( # 300 )
        //
        // NOTE: Ideally contactHeader should never be null.
        if ((contactHeader != null) && (contactHeaders.size() > 1)) {
            response = (SIPResponse) JainSipHelper.getMessageFactory().createResponse(Response.MULTIPLE_CHOICES, request);

        } else {
            // if one or less contact create redirect header with response
            // type-MOVED_TEMPORARILY ( #302)
            response = (SIPResponse) JainSipHelper.getMessageFactory().createResponse(Response.MOVED_TEMPORARILY, request);

        }
        //DSB TODO
        //SIPResponse.getReasonPhrase(statusCode);
        //response.setApp();


        Log.info("Created " + response.getStatusCode() + " response");

        ToHeader toHeader = response.getToHeader();
        if (toHeader.getTag() == null) {
            toHeader.setTag(Utils.getInstance().generateTag());
        }

        // add redirect header to the response.

        List<Contact> contactHeaderList = contactHeaders.getHeaderList();
        for (Contact contact : contactHeaderList) {
            response.addHeader(contact);
        }

        Log.debug("Leaving createRedirectResponse()");
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
        Log.debug("Entering sendServerInternalErrorResponse()");

        SIPResponse response = (SIPResponse) JainSipHelper.getMessageFactory().createResponse(Response.SERVER_INTERNAL_ERROR, request);

        ToHeader toHeader = response.getToHeader();
        if (toHeader.getTag() == null) {
            toHeader.setTag(SipTag.generateTag());
        }

        trans.respond(response);
        Log.debug("Leaving sendServerInternalErrorResponse()");
    }

    public static SIPResponse createNotFoundResponse(SIPRequest request) throws DhruvaException, ParseException {
        Log.debug("Entering createNotFoundResponse()");
        SIPResponse response = (SIPResponse) JainSipHelper.getMessageFactory().createResponse(Response.NOT_FOUND, request);

        ToHeader toHeader = response.getToHeader();
        if (toHeader.getTag() == null) {
            toHeader.setTag(SipTag.generateTag());
        }

        Log.debug("Leaving createNotFoundResponse()");

        return response;
    }

    /**
     * Utility function that sends 404 responses. Note, you must use a stateful transaction to send
     * the response.
     *
     * @param request The request original request that this response messeage is responding to.
     * @param trans The stateful proxy transaction that will be used to send the response.
     */
    public static void sendNotFoundResponse(SIPRequest request, ProxyTransaction trans)
            throws DhruvaException, ParseException {
        Log.debug("Entering sendNotFoundResponse()");
        trans.respond(createNotFoundResponse(request));
        Log.debug("Leaving sendNotFoundResponse()");
    }

    /**
     * This is the utility method that sends trying response
     *
     * @param trans The proxy transaction that will be used to send the response.
     */
    public static void sendByteBasedTryingResponse(ProxyTransaction trans) {
        Log.debug("Entering sendByteBasedTryingResponse()");
        trans.respond(null);
    }

    public static void sendTryingResponse(SIPRequest request, ProxyTransaction trans) throws ParseException {
        Log.debug("Entering sendTryingResponse()");
        SIPResponse response = (SIPResponse) JainSipHelper.getMessageFactory().createResponse(Response.TRYING, request);

        trans.respond(response);
    }

    public static void sendResponse(SIPResponse response, ProxyTransaction trans) {
        Log.debug("Entering sendResponse()");

        if (trans != null) {
            trans.respond(response);
            Log.debug("Sent response:" + NL + response);
        } else {
            Log.warn("DsProxyTransaction was null!");
        }
    }

    /** Creates a response of the given type, and tags the To header. */
    public static SIPResponse createResponse(int responseCode, SIPRequest request)
            throws DhruvaException, ParseException {
        Log.debug("Entering createResponse()");
        SIPResponse response = (SIPResponse) JainSipHelper.getMessageFactory().createResponse(responseCode, request);

        String tag = response.getToTag();
        Log.debug("To tag is " + tag);
        if (tag == null) {
            Log.debug("Generating To tag");
            ToHeader toHeader = response.getToHeader();
            if (toHeader != null) toHeader.setTag(SipTag.generateTag());
        }
        return response;
    }
}

