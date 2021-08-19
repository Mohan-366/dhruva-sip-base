package com.cisco.dsb.common.loadbalancer;

import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import java.security.SecureRandom;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA. User: rrachuma Date: Jul 22, 2008 Time: 12:41:05 PM To change this
 * template use File | Settings | File Templates.
 */
public class LBWeight extends LBBase {

  private static final SecureRandom randomGenerator = new SecureRandom();
  protected static final Logger logger = DhruvaLoggerFactory.getLogger(LBWeight.class);

  protected void setKey() {
    // To change body of implemented methods use File | Settings | File Templates.
  }

  protected ServerGroupElementInterface selectElement() {
    ServerGroupElementInterface selectedElement = null;
    ArrayList list = new ArrayList();
    float[] weightRanges = new float[domainsToTry.size()];
    float highestQ = -1;
    int totalWeight = 0;
    ServerGroupElementInterface sge;
    int index = 0;
    for (Object o : domainsToTry) {
      sge = (ServerGroupElementInterface) o;
      if (Float.compare(highestQ, sge.getQValue()) == 1 && totalWeight == 0) {
        highestQ = sge.getQValue();
      }
      if (Float.compare(highestQ, -1) == 0 || Float.compare(sge.getQValue(), highestQ) == 0) {
        highestQ = sge.getQValue();
        list.add(sge);
        if (sge.getWeight() > 0) {
          totalWeight = totalWeight + sge.getWeight();
          weightRanges[index++] = totalWeight;
        } else if (sge.getWeight() == 0) {
          weightRanges[index] = totalWeight;
          index++;
        }

      } else break;
    }
    StringBuffer output = new StringBuffer();
    for (Object o : list) {
      output.append(o.toString());
      output.append(", ");
    }
    logger.info("list of elements in order on which load balancing is done : " + output);

    if (list.size() == 1 && totalWeight == 0) {
      selectedElement = (ServerGroupElementInterface) list.get(0);
    } else {
      float random = randomGenerator.nextFloat();
      if ((Float.compare(random, 0.0f) == 0) && totalWeight != 0) {
        random += 0.0001f;
      }
      if (totalWeight != 0) {
        random = random * (float) totalWeight;
        for (int j = 0; j < list.size(); j++) {
          if (weightRanges[j] >= random) {
            selectedElement = (ServerGroupElementInterface) list.get(j);
            break;
          }
        }
      }
    }

    return selectedElement;
  }
}
