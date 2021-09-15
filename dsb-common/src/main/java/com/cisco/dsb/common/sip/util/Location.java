package com.cisco.dsb.common.sip.util;

// import com.cisco.dsb.trunk.loadbalancer.LBInterface;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import gov.nist.javax.sip.header.RouteList;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import lombok.Getter;
import lombok.Setter;

/**
 * This class is used to encapsulate various parameters for an outgoing request uri. This object is
 * the data structure used by XCL when dealing with next hops.
 *
 * <p>The default parameters for a Location is a null server group, Qvalue set to 1, and failover
 * turned off.
 *
 * @author Mitch Rappard
 */
public final class Location implements Cloneable, Comparable {

  public static final boolean USE_DEST_INFO_DEFAULT = false;
  public static final boolean PROCESS_ROUTE_DEFAULT = true;

  @Getter @Setter protected URI uri;
  @Getter @Setter protected String serverGroupName;
  @Getter @Setter protected RouteList routeHeaders;
  @Getter @Setter protected float qValue = DEFAULT_QVALUE;
  @Getter @Setter protected long lastUpdate = 0;
  @Getter @Setter protected boolean useDestInfo = USE_DEST_INFO_DEFAULT;
  @Getter @Setter protected boolean processRoute = PROCESS_ROUTE_DEFAULT;
  @Getter @Setter protected boolean removeExistingRoutes = false;
  @Getter @Setter protected boolean removeExistingRoutesOnRedirect = false;
  @Getter @Setter protected boolean copiedURIHeadersToRequest = false;

  @Getter @Setter protected DhruvaNetwork network = null;

  @Getter @Setter protected DhruvaNetwork defaultNetwork = null;
  @Getter @Setter protected int hashCode = -1;

  // @Getter @Setter protected LBInterface loadBalancer;

  @Getter @Setter RouteType routeType;

  public static float DEFAULT_QVALUE = (float) 1.0;

  public enum RouteType {
    DEFAULT_ROUTING,
    TRUNK_ROUTING,
  };

  /** our log object * */
  private static final Logger Log = DhruvaLoggerFactory.getLogger(Location.class);

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
    // location.setLoadBalancer(loadBalancer);
    location.setProcessRoute(processRoute);
    location.setUseDestInfo(useDestInfo);
    location.setNetwork(network);
    location.setDefaultNetwork(defaultNetwork);
    location.setRemoveExistingRoutes(removeExistingRoutes);
    location.setRemoveExistingRoutesOnRedirect(removeExistingRoutesOnRedirect);
    location.setCopiedURIHeadersToRequest(copiedURIHeadersToRequest);

    return location;
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

    Log.debug("Entering equals() for contact = " + lbURI);
    Log.debug("Current location is: " + this);

    // See if the URIs in the contact header match
    boolean equals = false;

    if (lbURI.getUri().equals(this.uri)) {
      // See if the Server groups match
      boolean serverGroupMatch = false;
      if (lbURI.getServerGroupName() != null && this.serverGroupName != null)
        serverGroupMatch = lbURI.getServerGroupName().equals(this.serverGroupName);
      else serverGroupMatch = lbURI.getServerGroupName() == null && serverGroupName == null;

      //      if (serverGroupMatch) {
      //        if (connectionID != null && lbURI.getConnectionID() != null)
      //          equals = connectionID.equals(lbURI.getConnectionID());
      //        else equals = connectionID == null && lbURI.getConnectionID() == null;
      //      }
    }
    Log.debug("Leaving equals(), returning " + equals);
    return equals;
  }

  public int hashCode() {
    if (hashCode != -1) return hashCode;

    int hashCode = 0;

    if (serverGroupName != null) hashCode += serverGroupName.hashCode();

    hashCode += uri.hashCode();

    return hashCode % Integer.MAX_VALUE;
  }

  public String toString() {
    return this.uri.toString()
        + ", Server Group: "
        + this.serverGroupName
        + ", Route Headers: "
        + this.routeHeaders
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
}
