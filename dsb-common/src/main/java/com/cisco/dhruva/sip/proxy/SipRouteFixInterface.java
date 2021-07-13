package com.cisco.dhruva.sip.proxy;

import javax.sip.address.SipURI;
import javax.sip.address.URI;

/**
 * This interface is used to implement the procedures described in rfc3261 paragraph 16.4 necessary
 * to interoperate with strict routers. When a request is received from the network, and an instance
 * of this class is provided to the Low Level transaction manager, <code>
 * DsSipRequest.lrFix(DsSipRouteFixInterface)</code> is is invoked on that message.
 */
public interface SipRouteFixInterface {
    /**
     * This method is invoked by the DsSipRequest to perform the procedures necessary to interoperate
     * with strict routers. For incoming requests, the class which implements this interface is first
     * asked to recognize the request URI. If the request URI is recognized, it is saved internally by
     * the invoking DsSipRequest as the LRFIX URI and replaced by the URI of the bottom Route header.
     * If the request URI is not recognized, the supplied interface is asked to recognize the URI of
     * the top Route header. If the top Route header's URI is recognized, it is removed and saved
     * internally as the LRFIX URI. If neither is recognized, the DsSipRequest's FIX URI is set to
     * null.
     *
     * @param uri a URI from the SIP request as described above
     * @param isRequestURI boolean to indicate whether the uri is a request-uri
     * @return <code>true</code> if the uri is recognized as a uri that was inserted into a
     *     Record-Route header, otherwise returns <code>false</code>
     */
    boolean recognize(URI uri, boolean isRequestURI);
}