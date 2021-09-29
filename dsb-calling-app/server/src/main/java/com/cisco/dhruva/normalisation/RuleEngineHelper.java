package com.cisco.dhruva.normalisation;

import com.cisco.dsb.common.util.TriFunction;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jeasy.rules.api.*;
import org.jeasy.rules.core.DefaultRulesEngine;

public class RuleEngineHelper {

  /**
   * From the given map of inputs, set of facts(key, value) are created. RuleEngine uses facts as
   * inputs to run the rules against ---- Note: key is always a 'String' ----
   */
  public static Function<Map<String, Object>, Facts> getFacts =
      map -> {
        Facts facts = new Facts();
        map.forEach(facts::put);
        return facts;
      };

  /**
   * From the given list of inputs, set of rules(object) are created. RuleEngine runs by executing
   * the rules it is registered with ---- Note: Rules will be executed in the order they are
   * registered unless they have a priority mentioned ----
   */
  public static Function<List<Object>, Rules> getNormRules =
      rlist -> {
        Rules rules = new Rules();
        rlist.forEach(rules::register);
        return rules;
      };

  /** Returns a new default rule engine configured based on input parameters */
  private static Function<RulesEngineParameters, DefaultRulesEngine> createDefaultRulesEngine =
      params -> {
        if (Objects.nonNull(params)) return new DefaultRulesEngine(params);
        return new DefaultRulesEngine();
      };

  /** Returns a new default rule engine and registers listeners(optional) to it */
  public static TriFunction<
          RulesEngineParameters, RuleListener, RulesEngineListener, DefaultRulesEngine>
      getSimpleDefaultRuleEngine =
          (params, ruleListener, rulesEngineListener) -> {
            DefaultRulesEngine rulesEngine = createDefaultRulesEngine.apply(params);

            if (Objects.nonNull(ruleListener)) {
              rulesEngine.registerRuleListener(ruleListener);
            }
            if (Objects.nonNull(rulesEngineListener)) {
              rulesEngine.registerRulesEngineListener(rulesEngineListener);
            }
            return rulesEngine;
          };
}
