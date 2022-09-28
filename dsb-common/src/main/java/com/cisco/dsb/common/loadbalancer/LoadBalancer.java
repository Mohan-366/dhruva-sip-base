package com.cisco.dsb.common.loadbalancer;

import java.util.Collection;
import java.util.TreeSet;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.Setter;

@CustomLog
public final class LoadBalancer {
  private LBElement currentElement;
  private TreeSet<LBElement> elementsToTry;
  @Setter private String key;
  // algo to choose an Element from given elements
  private BiFunction<TreeSet<LBElement>, String, LBElement> selectElement;

  private LoadBalancer() {}

  /**
   * Use LBFactory to create loadbalancer, as it takes care of setting appropriate Key for given
   * LBType. Or use setKey() to set the Key. Use getNextElement() to get next element based on LB
   * algo. Note that initial LB won't have any element selected i.e getCurrentElement() will be null
   *
   * @param loadBalancable - Object that needs to be loadbalanced
   * @return LoadBalancer
   */
  public static LoadBalancer of(LoadBalancable loadBalancable) {
    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.selectElement = loadBalancable.getLbType().getSelectElement();
    loadBalancer.initialiseTreeSet(loadBalancable.getElements());
    return loadBalancer;
  }

  /**
   * This method performs the appropriate load balancing algorithm to determine the next element.
   * Successive calls to this method during the same transaction should return another potential
   * next element, but SHOULD NOT consider <code>LBElement</code>s which have already been attempted
   * as valid next element.
   *
   * @return the <code>LBElement</code> that is the next best hop.
   */
  @Nullable
  public LBElement getNextElement() {
    if (elementsToTry.isEmpty()) {
      logger.warn("No more Elements to try");
      return null;
    }
    LBElement selectedElement = selectElement.apply(elementsToTry, key);
    elementsToTry.remove(selectedElement);
    currentElement = selectedElement;
    return currentElement;
  }

  /**
   * Gets the currently selected element
   *
   * @return the current element
   */
  public LBElement getCurrentElement() {
    return currentElement;
  }

  /**
   * create a TreeSet
   *
   * @param lbElements - Elements to be loadbalanced
   */
  private void initialiseTreeSet(Collection<? extends LBElement> lbElements) {
    elementsToTry = new TreeSet<>(lbElements);
  }

  public TreeSet<? extends LBElement> getElementsToTry() {
    return this.elementsToTry;
  }

  public boolean isEmpty() {
    return this.elementsToTry.isEmpty();
  }
}
