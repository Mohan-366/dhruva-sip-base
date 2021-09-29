package com.cisco.dhruva.normalisation;

import static org.mockito.Mockito.*;

import java.util.List;
import org.jeasy.rules.api.*;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RuleEngineHelperTest {

  @Test(
      description =
          "DefaultRuleEngine with default parameters, ruleListener and RuleEngineListener")
  public void testRuleEngineWithDefaultValues() {
    DefaultRulesEngine rulesEngine =
        RuleEngineHelper.getSimpleDefaultRuleEngine.apply(null, null, null);

    RulesEngineParameters actual_params = rulesEngine.getParameters();
    Assert.assertFalse(actual_params.isSkipOnFirstAppliedRule()); // default is false
    Assert.assertFalse(actual_params.isSkipOnFirstFailedRule()); // default is false
    Assert.assertFalse(actual_params.isSkipOnFirstNonTriggeredRule()); // default is false

    Assert.assertEquals(rulesEngine.getRuleListeners().size(), 0);
    Assert.assertEquals(rulesEngine.getRulesEngineListeners().size(), 0);
  }

  @Test(
      description = "DefaultRuleEngine with custom parameters, ruleListener and RuleEngineListener")
  public void testRuleEngineWithCustomValues() {
    RulesEngineParameters params = new RulesEngineParameters().skipOnFirstAppliedRule(true);
    RuleListener ruleListener = mock(RuleListener.class);
    RulesEngineListener rulesEngineListener = mock(RulesEngineListener.class);

    DefaultRulesEngine rulesEngine =
        RuleEngineHelper.getSimpleDefaultRuleEngine.apply(
            params, ruleListener, rulesEngineListener);

    RulesEngineParameters actual_params = rulesEngine.getParameters();
    Assert.assertTrue(actual_params.isSkipOnFirstAppliedRule()); // reflects the value we set
    Assert.assertFalse(actual_params.isSkipOnFirstFailedRule()); // default is false
    Assert.assertFalse(actual_params.isSkipOnFirstNonTriggeredRule()); // default is false

    List<RuleListener> actual_ruleListeners = rulesEngine.getRuleListeners();
    Assert.assertEquals(actual_ruleListeners.size(), 1);
    Assert.assertTrue(actual_ruleListeners.contains(ruleListener));

    List<RulesEngineListener> actual_EngineListeners = rulesEngine.getRulesEngineListeners();
    Assert.assertEquals(actual_EngineListeners.size(), 1);
    Assert.assertTrue(actual_EngineListeners.contains(rulesEngineListener));
  }
}
