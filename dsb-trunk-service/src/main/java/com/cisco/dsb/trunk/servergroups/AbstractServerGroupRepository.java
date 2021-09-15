/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
/*
 * User: bjenkins
 * Date: Mar 25, 2002
 * Time: 2:44:56 PM
 */
package com.cisco.dsb.trunk.servergroups;

import java.util.*;
import org.springframework.stereotype.Component;

/**
 * This class servers as a superclass for all types of server group repositories. Because it is a
 * general-purpose class, it defines a number of methods for creating new instances, accessing data
 * members, adding and removing Server Groups, and creating a copy of an instance while preserving
 * the internal reference to some data members.
 */
@Component
public class AbstractServerGroupRepository {

  private HashMap<String, AbstractServerGroup> serverGroups;

  protected AbstractServerGroupRepository() {
    serverGroups = new HashMap();
  }

  /**
   * Adds a new server group to the repository.
   *
   * @param sg the new server group.
   * @return <code>true</code> if a modification was made.
   * @throws DuplicateServerGroupException
   */
  public boolean addServerGroup(AbstractServerGroup sg) throws DuplicateServerGroupException {
    if (serverGroups.get(sg.getName()) != null)
      throw new DuplicateServerGroupException("Server group " + sg.getName() + " already exists");
    serverGroups.put(sg.getName(), sg);
    return true;
  }

  /**
   * Adds a new server group element to the repository.
   *
   * @param destinationServerGroupName the server group getting the new element.
   * @param element the new element.
   * @throws DuplicateServerGroupEntryException
   * @throws NonExistantServerGroupException
   * @throws CircularReferenceException
   */
  public final void addServerGroupElement(
      String destinationServerGroupName, ServerGroupElement element)
      throws DuplicateServerGroupEntryException, NonExistantServerGroupException,
          CircularReferenceException {
    // if (Log.on && Log.isTraceEnabled())
    // Log.trace(
    //   "Entering addServerGroupElement(" + destinationServerGroupName + ", " + element + ")");

    AbstractServerGroup sg = (AbstractServerGroup) serverGroups.get(destinationServerGroupName);
    if (sg == null)
      throw new NonExistantServerGroupException(
          "Server group " + destinationServerGroupName + " does not exist");

    if (!element.isNextHop()) {
      AbstractServerGroupPlaceholder sgp = (AbstractServerGroupPlaceholder) element;
      checkServerGroup(destinationServerGroupName, sgp.getServerGroupName());
    }

    if (!addElement(element, sg))
      throw new DuplicateServerGroupEntryException(
          "Element " + element + " already exists in server group " + destinationServerGroupName);

    // if (Log.on && Log.isTraceEnabled()) Log.trace("Leaving addServerGroupElement()");
  }

  /**
   * Adds an ServerGroupElement to the specified AbstractServerGroup. This method is called by
   * addServerGroupElement(). Subclasses should override this method if they want to do anything
   * special when adding an ServerGroupElement.
   *
   * @param element the ServerGroupElement being added.
   * @param sg the AbstractServerGroup
   * @return <code>true</code> if the operation was successful, <code>false</code> otherwise
   * @see #addServerGroupElement(String, ServerGroupElement)
   */
  protected boolean addElement(ServerGroupElement element, AbstractServerGroup sg) {
    return sg.addElement(element);
  }

  /**
   * Deletes all server groups in the repository.
   *
   * @return <code>true</code> if a modification was made.
   */
  public boolean deleteAllServerGroups() {
    if (serverGroups.size() > 0) {
      serverGroups.clear();
      return true;
    }
    return false;
  }

  /**
   * Deletes a server groups from the repository.
   *
   * @return the deleted server group if the operation was successful, <code>null</code> otherwise.
   *     The deleted server group is returned so that the reference count for each next hop in the
   *     server group can be decremented.
   */
  public AbstractServerGroup deleteServerGroup(String serverGroupName) {
    // if (Log.on && Log.isTraceEnabled()) Log.trace("Entering deleteServerGroup()");
    AbstractServerGroup removedSG = (AbstractServerGroup) serverGroups.remove(serverGroupName);
    if (removedSG != null) {
      for (Iterator i = serverGroups.values().iterator(); i.hasNext(); ) {
        AbstractServerGroup sg = (AbstractServerGroup) i.next();
        AbstractServerGroupPlaceholder sgp =
            (AbstractServerGroupPlaceholder) sg.get(serverGroupName);
        if (sgp != null) sg.removeSameReferenceTo(sgp);
      }
    }
    // if (Log.on && Log.isTraceEnabled()) Log.trace("Leaving deleteServerGroup()");
    return removedSG;
  }

  /**
   * Deletes a server group element from the server group.
   *
   * @param serverGroupName the name of the server group.
   * @param sge the server group element to delete.
   * @return <code>true</code> if a modification was made.
   */
  public final ServerGroupElement deleteServerGroupElement(
      String serverGroupName, ServerGroupElement sge) {
    // if (Log.on && Log.isTraceEnabled()) Log.trace("Entering deleteServerGroupElement()");
    ServerGroupElement realReference = null;
    if ((serverGroupName != null) && (sge != null)) {
      realReference = removeElement(sge, serverGroupName);
    }
    // if (Log.on && Log.isTraceEnabled()) Log.trace("Leaving deleteServerGroupElement()");
    return realReference;
  }

  public final Set deleteAllServerGroupElements(String serverGroupName) {
    Set s = null;
    if (serverGroupName != null) {
      s = removeAllElements(serverGroupName);
    }
    return s;
  }

  /**
   * Performs the actual removal of the specified element from the AbstractServerGroup. This method
   * is called by removeElement(ServerGroupElement, String). Subclasses should override this method
   * if they want to do anything special when removing an ServerGroupElement.
   *
   * @param element the ServerGroupElement being added.
   * @param sg the AbstractServerGroup
   * @see #removeElement(ServerGroupElement, String)
   */
  protected ServerGroupElement removeElement(ServerGroupElement element, AbstractServerGroup sg) {
    ServerGroupElement removed = null;
    if (sg != null) removed = sg.removeSameReferenceTo(element);
    return removed;
  }

  /**
   * Performs the actual removal of the specified element from the AbstractServerGroup. This method
   * is called by deleteServerGroupElement(). Subclasses should override this method if they want to
   * do anything special when removing an ServerGroupElement.
   *
   * @param element the ServerGroupElement being added.
   * @param serverGroupName the AbstractServerGroup
   * @see #deleteServerGroupElement(String, ServerGroupElement)
   */
  protected ServerGroupElement removeElement(ServerGroupElement element, String serverGroupName) {
    AbstractServerGroup sg = (AbstractServerGroup) serverGroups.get(serverGroupName);
    return removeElement(element, sg);
  }

  protected Set removeAllElements(String serverGroupName) {
    Set s = null;
    AbstractServerGroup sg = getServerGroup(serverGroupName);
    if (sg != null) s = sg.removeAll();
    return s;
  }

  /** @return the list of server groups. */
  public final HashMap getServerGroups() {
    return serverGroups;
  }

  public List getServerGroups(String network) {
    List sgList = new LinkedList();
    for (Iterator iterator = serverGroups.values().iterator(); iterator.hasNext(); ) {
      AbstractServerGroup sg = (AbstractServerGroup) iterator.next();
      if (sg.getNetwork().toString().equals(network)) {
        sgList.add(sg);
      }
    }
    return (sgList);
  }

  private void checkServerGroup(String destinationServerGroupName, String addingServerGroupName)
      throws NonExistantServerGroupException, CircularReferenceException {
    AbstractServerGroup sg = (AbstractServerGroup) serverGroups.get(addingServerGroupName);
    if (sg == null)
      throw new NonExistantServerGroupException(
          "Server group "
              + addingServerGroupName
              + " does not exist, cannot add it to server group "
              + destinationServerGroupName);
    if (addingServerGroupName.equalsIgnoreCase(destinationServerGroupName))
      throw new CircularReferenceException(
          "Cannot add server group " + addingServerGroupName + " to itself");
    if (sg.containsReferenceTo(destinationServerGroupName, serverGroups))
      throw new CircularReferenceException(
          "Server group "
              + addingServerGroupName
              + " or one of its subgroups contains a reference to "
              + destinationServerGroupName);
  }

  /**
   * Gets the AbstractServerGroup with the given name
   *
   * @param sg_name the name of the AbstractServerGroup to get
   * @return the AbstractServerGroup with the given name, or <code>null</code> if it doesn't exist.
   */
  public final AbstractServerGroup getServerGroup(String sg_name) {
    return serverGroups.get(sg_name);
  }
}
