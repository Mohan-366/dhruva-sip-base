package com.cisco.dsb.common.loadbalancer;

public final class HashAlgorithm {
  /** our log object * */

  /**
   * Select the index based on the key and the size of the list
   *
   * @param key Key used to select the element.
   * @param listSize The size of the list of those elements available.
   * @return The index of the next element in the element list.
   */
  public static int selectIndex(String key, int listSize) {
    if (key == null) return 0;
    if (listSize <= 0) return -1;

    int index = Math.abs(key.hashCode() % listSize);

    return index;
  }
}
