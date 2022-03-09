package com.cisco.dhruva.util;

import java.text.ParseException;

@FunctionalInterface
public interface CustomConsumer<One, Two, Three> {
  public void consume(One one, Two two, Three three) throws ParseException;
}
