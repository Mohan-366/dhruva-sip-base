package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.log.Trace;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.SIPHeaderList;
import javax.sip.address.SipURI;
import javax.sip.address.URI;

/**
 * This class is used to encapsulate various parameters for an outgoing request uri. This object is
 * the data structure used by XCL when dealing with next hops.
 *
 * <p>The default parameters for a Location is a null server group, Qvalue set to 1, and failover
 * turned off.
 *
 * @author Mitch Rappard
 */

// DSB TODO
interface LBInterface {}

public final class Location implements Cloneable, Comparable {

  public static final boolean USE_DEST_INFO_DEFAULT = false;
  public static final boolean PROCESS_ROUTE_DEFAULT = true;

  protected URI uri;
  protected String serverGroupName;
  protected RouteList routeHeaders;
  protected String connectionID;
  protected float qValue = DEFAULT_QVALUE;
  protected long lastUpdate = 0;
  protected boolean useDestInfo = USE_DEST_INFO_DEFAULT;
  protected boolean processRoute = PROCESS_ROUTE_DEFAULT;
  protected boolean removeExistingRoutes = false;
  protected boolean removeExistingRoutesOnRedirect = false;
  protected boolean copiedURIHeadersToRequest = false;

  protected DhruvaNetwork network = null;
  protected DhruvaNetwork defaultNetwork = null;
  protected int hashCode = -1;

  protected LBInterface lb;

  public static float DEFAULT_QVALUE = (float) 1.0;

  /** our log object * */
  private static final Trace Log = Trace.getTrace(Location.class.getName());

  /**
   * Create a Location with the given URI, and all parameters set to their default.
   *
   * @param uri The DsURI to send to.
   */
  public Location(URI uri) {
    this(uri, null, null, DEFAULT_QVALUE, 0);
  }

  /**
   * Create a Location with all the parameters specified.
   *
   * @param uri The DsURI to send to.
   * @param serverGroupName The server group name that should be used when load balancing.
   * @param qValue The qValue for this contact/uri.
   */
  public Location(SipURI uri, RouteList routeHeaders, String serverGroupName, float qValue) {
    this(uri, routeHeaders, serverGroupName, qValue, 0);
  }

  public Location(
      URI uri, RouteList routeHeaders, String serverGroupName, float qValue, long lastUpdate) {
    this.uri = uri;
    this.serverGroupName = serverGroupName;
    this.routeHeaders = routeHeaders;
    this.qValue = qValue;
    this.lastUpdate = lastUpdate;
  }

  /** @noinspection CloneDoesntCallSuperClone */
  public Object clone() {
    URI clonedURI = (URI) uri.clone();
    Location location = new Location(clonedURI, routeHeaders, serverGroupName, qValue, lastUpdate);
    location.setLoadBalancer(lb);
    location.setProcessRoute(processRoute);
    location.setUseDestInfo(useDestInfo);
    location.setNetwork(network);
    location.setDefaultNetwork(defaultNetwork);
    location.setConnectionID(connectionID);
    location.setRemoveExistingRoutes(removeExistingRoutes);
    location.setRemoveExistingRoutesOnRedirect(removeExistingRoutesOnRedirect);
    location.setCopiedURIHeadersToRequest(copiedURIHeadersToRequest);

    return location;
  }

  public void setDefaultNetwork(DhruvaNetwork defaultNetwork) {
    this.defaultNetwork = defaultNetwork;
  }

  public DhruvaNetwork getDefaultNetwork() {
    return defaultNetwork;
  }

  public URI getURI() {
    return this.uri;
  }

  public void setNetwork(DhruvaNetwork network) {
    this.network = network;
  }

  public DhruvaNetwork getNetwork() {
    return network;
  }

  public String getServerGroupName() {
    return this.serverGroupName;
  }

  public void setServerGroupName(String sgName) {
    this.serverGroupName = sgName;
  }

  public SIPHeaderList getRouteHeaders() {
    return routeHeaders;
  }

  public void setRouteHeaders(RouteList routeHeaders) {
    this.routeHeaders = routeHeaders;
  }

  public LBInterface getLoadBalancer() {
    return this.lb;
  }

  public void setLoadBalancer(LBInterface lb) {
    this.lb = lb;
  }

  public void setUseDestInfo(boolean b) {
    this.useDestInfo = b;
  }

  public boolean useDestInfo() {
    return useDestInfo;
  }

  public long getLastUpdate() {
    return lastUpdate;
  }

  public boolean equals(Object obj) {
    if (obj == null) return false;
    else return equals((Location) obj);
  }

  /**
   * Calls the equals method of the internal URI, compares the server group name, and the
   * connection-id. If all of these are equal, then we return true.
   *
   * @param lbURI The Location to compare.
   * @return <code>true</code> if the URI and server group are the same, false otherwise.
   */
  public boolean equals(Location lbURI) {

    if (Log.on && Log.isTraceEnabled()) {
      Log.debug("Entering equals() for contact = " + lbURI);
      Log.debug("Current location is: " + this);
    }

    // See if the URIs in the contact header match
    boolean equals = false;

    if (lbURI.getURI().equals(this.uri)) {
      // See if the Server groups match
      boolean serverGroupMatch = false;
      if (lbURI.getServerGroupName() != null && this.serverGroupName != null)
        serverGroupMatch = lbURI.getServerGroupName().equals(this.serverGroupName);
      else serverGroupMatch = lbURI.getServerGroupName() == null && serverGroupName == null;

      if (serverGroupMatch) {
        if (connectionID != null && lbURI.getConnectionID() != null)
          equals = connectionID.equals(lbURI.getConnectionID());
        else equals = connectionID == null && lbURI.getConnectionID() == null;
      }
    }
    if (Log.on && Log.isTraceEnabled()) Log.trace("Leaving equals(), returning " + equals);
    return equals;
  }

  public int hashCode() {
    if (hashCode != -1) return hashCode;

    int hashCode = 0;

    if (serverGroupName != null) hashCode += serverGroupName.hashCode();

    if (connectionID != null) hashCode += connectionID.hashCode();

    hashCode += uri.hashCode();

    return hashCode % Integer.MAX_VALUE;
  }

  public String toString() {
    return this.uri.toString()
        + ", Server Group: "
        + this.serverGroupName
        + ", Route Headers: "
        + this.routeHeaders
        + ", Connection-ID = "
        + connectionID
        + ", Q-value= "
        + this.qValue
        + ", Process Route: "
        + processRoute
        + ", Use Dest Info: "
        + useDestInfo
        + ", Network="
        + network
        + ", Default Network="
        + defaultNetwork;
  }

  /**
   * Compares this object with the specified object for order. Returns a negative integer, zero, or
   * a positive integer as this object is less than, equal to, or greater than the specified object.
   *
   * <p>The implementor must ensure <tt>sgn(x.compareTo(y)) == -sgn(y.compareTo(x))</tt> for all
   * <tt>x</tt> and <tt>y</tt>. (This implies that <tt>x.compareTo(y)</tt> must throw an exception
   * iff <tt>y.compareTo(x)</tt> throws an exception.)
   *
   * <p>The implementor must also ensure that the relation is transitive: <tt>(x.compareTo(y)&gt;0
   * &amp;&amp; y.compareTo(z)&gt;0)</tt> implies <tt>x.compareTo(z)&gt;0</tt>.
   *
   * <p>Finally, the implementer must ensure that <tt>x.compareTo(y)==0</tt> implies that
   * <tt>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</tt>, for all <tt>z</tt>.
   *
   * <p>It is strongly recommended, but <i>not</i> strictly required that <tt>(x.compareTo(y)==0) ==
   * (x.equals(y))</tt>. Generally speaking, any class that implements the <tt>Comparable</tt>
   * interface and violates this condition should clearly indicate this fact. The recommended
   * language is "Note: this class has a natural ordering that is inconsistent with equals."
   *
   * @param o the Object to be compared.
   * @return a negative integer, zero, or a positive integer as this object should appear after,
   *     equal to, or before the specified object.
   * @throws ClassCastException if the specified object's type prevents it from being compared to
   *     this Object.
   */
  public int compareTo(Object o) {
    Location loc = (Location) o;
    int returnVal = 0;
    if (getQValue() > loc.getQValue()) returnVal = -1;
    else if (getQValue() < loc.getQValue()) returnVal = 1;
    else {
      if (getLastUpdate() > loc.getLastUpdate()) returnVal = -1;
      else if (getLastUpdate() < loc.getLastUpdate()) returnVal = 1;
    }
    return returnVal;
  }

  public float getQValue() {
    return this.qValue;
  }

  public void setQValue(float qValue) {
    this.qValue = qValue;
  }

  public void setURI(SipURI newURI) {
    this.uri = newURI;
  }

  public boolean processRoute() {
    return processRoute;
  }

  public void setProcessRoute(boolean processRoute) {
    this.processRoute = processRoute;
  }

  public String getConnectionID() {
    return connectionID;
  }

  public void setConnectionID(String connectionID) {
    this.connectionID = connectionID;
  }

  public void setRemoveExistingRoutes(boolean flag) {
    removeExistingRoutes = flag;
  }

  public boolean getRemoveExistingRoutes() {
    return removeExistingRoutes;
  }

  public void setRemoveExistingRoutesOnRedirect(boolean flag) {
    removeExistingRoutesOnRedirect = flag;
  }

  public boolean getRemoveExistingRoutesOnRedirect() {
    return removeExistingRoutesOnRedirect;
  }

  public void setCopiedURIHeadersToRequest(boolean flag) {
    copiedURIHeadersToRequest = flag;
  }

  public boolean getCopiedURIHeadersToRequest() {
    return copiedURIHeadersToRequest;
  }
}
