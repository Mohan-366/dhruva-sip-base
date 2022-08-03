package com.cisco.dsb.common.normalization;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public interface Normalization {

  public Consumer ingressNormalize();

  public Consumer egressPreNormalize();

  public BiConsumer egressPostNormalize();

  public Consumer setNormForFutureResponse();
}
