/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
package com.cisco.dsb.loadbalancer;

/**
 * Hash based algorithm used to select the next server/hop. It takes a key to ensure that those
 * requests with the same key will go to the same server/hop.
 */
public class HashAlgorithm {

  /** our log object * */

  /**
   * Select the index based on the key and the size of the list
   *
   * @param key Key used to select the server/hop.
   * @param listSize The size of the list of those servers available.
   * @return The index of the next hop in the server list.
   */
  public static int selectIndex(String key, int listSize) {

    if (listSize <= 0) return -1;

    int index = Math.abs(key.hashCode() % listSize);

    return index;
  }
}
