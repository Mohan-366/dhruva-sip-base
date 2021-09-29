package com.cisco.dhruva.normalisation;

import lombok.CustomLog;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngineListener;

@CustomLog
public class RulesEngineListenerImpl implements RulesEngineListener {

  /**
   * Triggered before evaluating the rule set.
   *
   * @param rules to fire
   * @param facts present before firing rules
   */
  public void beforeEvaluate(Rules rules, Facts facts) {
    logger.debug(
        "Before ruleSet execution: \nRule = " + rules.toString() + "\nFacts: " + facts.asMap());
  }

  /**
   * Triggered after executing the rule set
   *
   * @param rules fired
   * @param facts present after firing rules
   */
  public void afterExecute(Rules rules, Facts facts) {
    logger.debug(
        "After ruleSet execution: \nRule = " + rules.toString() + "\nFacts: " + facts.asMap());
  }
}
