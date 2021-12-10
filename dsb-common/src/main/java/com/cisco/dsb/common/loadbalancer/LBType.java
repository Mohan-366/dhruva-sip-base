package com.cisco.dsb.common.loadbalancer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import lombok.Getter;

@SuppressFBWarnings
public enum LBType {
  MS_ID(hashBased()), // using MS_ID key to pick the element if qValue is equal
  ONCE(single()), // picking only one element randomly from the treeSet, ignoring highestQ
  HIGHEST_Q(highestQ()), // picking the first element of the treeset
  HUNT(weightBased()), // HUNT group using weight based algo to get next element
  WEIGHT(
      weightBased()); // if qValue is equal, then weight based random distribution is used to pick
  // the next element

  @Getter private BiFunction<TreeSet<LBElement>, String, LBElement> selectElement;

  LBType(BiFunction<TreeSet<LBElement>, String, LBElement> selectElement) {
    this.selectElement = selectElement;
  }

  private static BiFunction<TreeSet<LBElement>, String, LBElement> hashBased() {
    return (elementsToSelect, key) -> {
      List<LBElement> highestQvalueElements = getHighestQElements(elementsToSelect);
      LBElement selectedElement;
      int index = HashAlgorithm.selectIndex(key, highestQvalueElements.size());
      selectedElement = (index != -1 ? highestQvalueElements.get(index) : null);
      return selectedElement;
    };
  }

  private static List<LBElement> getHighestQElements(TreeSet<LBElement> elementsToSelect) {
    float highestQValue = elementsToSelect.first().getPriority();
    ArrayList<LBElement> highestQvalueElements = new ArrayList<>();
    for (LBElement element : elementsToSelect) {
      if (Float.compare(highestQValue, element.getPriority()) == 0)
        highestQvalueElements.add(element);
      else break;
    }
    return highestQvalueElements;
  }

  private static BiFunction<TreeSet<LBElement>, String, LBElement> weightBased() {
    return (elementsToSelect, key) -> {
      List<LBElement> highestQvalueElements;
      highestQvalueElements = getHighestQElements(elementsToSelect);
      int[] weights = new int[highestQvalueElements.size()];
      weights[0] = highestQvalueElements.get(0).getWeight();
      for (int i = 1; i < weights.length; i++) {
        weights[i] = highestQvalueElements.get(i).getWeight() + weights[i - 1];
      }
      // Binary search to find index of rand
      int first, mid, last;
      int rand = ThreadLocalRandom.current().nextInt(weights[weights.length - 1]);
      first = 0;
      last = weights.length - 1;
      mid = (first + last) / 2;
      while (first < last) {
        if (weights[mid] < rand) {
          first = mid + 1;
        } else if (weights[mid] > rand) {
          last = mid - 1;
        } else {
          break;
        }
        mid = (first + last) / 2;
      }
      return highestQvalueElements.get(mid);
    };
  }

  /**
   * Select only one element and clear the treeset, so that next call does not give any element. No
   * priority given to qValue
   *
   * @return LBElement - selected element
   */
  private static BiFunction<TreeSet<LBElement>, String, LBElement> single() {
    return (elementsToSelect, key) -> {
      int index = ThreadLocalRandom.current().nextInt(elementsToSelect.size());
      int i = 0;
      LBElement selectedElement = null;
      for (LBElement element : elementsToSelect) {
        if (i == index) {
          selectedElement = element;
          elementsToSelect.clear();
          break;
        }
        i++;
      }
      return selectedElement;
    };
  }

  private static BiFunction<TreeSet<LBElement>, String, LBElement> highestQ() {
    return (elementsToSelect, key) -> {
      List<LBElement> highestQvalueElements = getHighestQElements(elementsToSelect);
      LBElement selectedElement =
          highestQvalueElements.get(
              ThreadLocalRandom.current().nextInt(highestQvalueElements.size()));
      return selectedElement;
    };
  }
}
