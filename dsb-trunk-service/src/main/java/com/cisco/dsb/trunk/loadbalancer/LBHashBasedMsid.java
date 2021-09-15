package com.cisco.dsb.trunk.loadbalancer;

import com.cisco.dsb.trunk.servergroups.ServerGroupElement;
import java.util.ArrayList;
import java.util.TreeSet;

public class LBHashBasedMsid extends LBBase {

  public static final String MPARAM_START_STRING = "m=";
  public static final String MPARAM_END_STRING = "a=";
  public static final String MPARAM_DS_STRING = "applicationsharing";
  public static final String MPARAM_AV_STRING = "audio";
  protected boolean isDSCall = false;
  protected ArrayList lbList = new ArrayList();

  @Override
  public final ServerInterface getServer() {

    lastTried = pickServer();
    return lastTried;
  }

  public final ServerInterface pickServer() {

    lastTried = null;

    if (domainsToTry == null) initializeDomains();
    if (domainsToTry.isEmpty()) {
      return null;
    }

    ServerGroupElementInterface selectedElement = selectElement();
    boolean isMyNextHop = true;
    if (selectedElement == null) {
      domainsToTry.clear();
    } else if (isMyNextHop) {
      domainsToTry.remove(selectedElement);

      lastTried = (ServerInterface) selectedElement;
    }

    return lastTried;
  }

  protected final ServerGroupElementInterface selectElement() {
    ServerGroupElementInterface selectedElement = null;

    if (domainsToTry.isEmpty()) {
      return null;
    }
    if (lbList != null && !lbList.isEmpty()) {
      selectedElement = (ServerGroupElementInterface) lbList.get(0);
      lbList.remove(0);
      domainsToTry.remove(selectedElement);
    } else {
      selectedElement = getElementFromLB();
    }
    ServerInterface nextHop = (ServerInterface) selectedElement;
    if (isDsCall()) {
      return selectedElement;
    } else if (nextHop != null && nextHop.isAvailable()) {
      return selectedElement;
    } else {
      while (nextHop != null && !nextHop.isAvailable()) {
        if (lbList.isEmpty()) {

          selectedElement = selectElement();
          return selectedElement;
        }
        selectedElement = (ServerGroupElementInterface) lbList.get(0);
        lbList.remove(0);
        domainsToTry.remove(selectedElement);
        nextHop = (ServerInterface) selectedElement;
      }
    }
    return selectedElement;
  }

  private ServerGroupElementInterface getElementFromLB() {
    ServerGroupElementInterface selectedElement = null;
    float highestQ = -1;
    for (Object o : domainsToTry) {
      ServerGroupElementInterface sge = (ServerGroupElementInterface) o;
      if (Float.compare(highestQ, -1) == 0) {
        highestQ = sge.getQValue();
        lbList.add(sge);
      } else if (Float.compare(sge.getQValue(), highestQ) == 0) {
        lbList.add(sge);
      } else break;
    }

    if (lbList.size() == 1) {
      selectedElement = (ServerGroupElementInterface) lbList.get(0);
      lbList.remove(0);
      domainsToTry.remove(selectedElement);
    } else {
      selectedElement = getElementByHashing();
    }
    return selectedElement;
  }

  private ServerGroupElementInterface getElementByHashing() {
    ServerGroupElementInterface selectedElement = null;
    String hashKey = key;

    int index = HashAlgorithm.selectIndex(hashKey, lbList.size());
    if (index != -1) {
      selectedElement = (ServerGroupElementInterface) lbList.get(index);
      lbList.remove(index);
      domainsToTry.remove(selectedElement);
    }
    return selectedElement;
  }

  @Override
  protected void setKey() {
    // Defined in its implementation class
  }

  public boolean isDsCall() {
    String mParam = null;

    return isDSCall;
  }

  private boolean setDsFlag(String[] mParamArr) {
    for (String s : mParamArr) {
      if (s.contains(MPARAM_DS_STRING)) {
        isDSCall = true;
        break;
      }
    }
    return isDSCall;
  }

  public final void initializeDomains() {
    ServerGroupInterface serverGroup = this.serverGroup;
    domainsToTry = new TreeSet();
    if (serverGroup == null) {

      return;
    }
    for (Object o : serverGroup.getElements()) {
      ServerGroupElementInterface sge = (ServerGroupElementInterface) o;
      if (sge.isNextHop()) {
        domainsToTry.add(sge);
      } else {
        if (((ServerGroupElement) sge).isAvailable()) {
          domainsToTry.add(sge);
        }
      }
    }
  }
}
