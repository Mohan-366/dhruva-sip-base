package com.cisco.dsb.common.normalization;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public interface Normalization {

  public Consumer preNormalize();

  public BiConsumer postNormalize();

  public Consumer setNormForFutureResponse();
}
