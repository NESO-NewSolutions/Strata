/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calc.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.CalculationTarget;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.market.MarketDataFeed;
import com.opengamma.strata.basics.market.MarketDataId;
import com.opengamma.strata.basics.market.ObservableId;
import com.opengamma.strata.basics.market.TestObservableKey;
import com.opengamma.strata.calc.CalculationRules;
import com.opengamma.strata.calc.Column;
import com.opengamma.strata.calc.config.MarketDataRule;
import com.opengamma.strata.calc.config.MarketDataRules;
import com.opengamma.strata.calc.config.Measure;
import com.opengamma.strata.calc.config.ReportingRules;
import com.opengamma.strata.calc.config.pricing.DefaultFunctionGroup;
import com.opengamma.strata.calc.config.pricing.DefaultPricingRules;
import com.opengamma.strata.calc.config.pricing.PricingRule;
import com.opengamma.strata.calc.config.pricing.PricingRules;
import com.opengamma.strata.calc.marketdata.CalculationMarketData;
import com.opengamma.strata.calc.marketdata.FunctionRequirements;
import com.opengamma.strata.calc.marketdata.MarketDataRequirements;
import com.opengamma.strata.calc.marketdata.TestKey;
import com.opengamma.strata.calc.marketdata.mapping.DefaultMarketDataMappings;
import com.opengamma.strata.calc.marketdata.mapping.MarketDataMappings;
import com.opengamma.strata.calc.runner.function.CalculationSingleFunction;

/**
 * Test {@link CalculationTasks}.
 */
@Test
public class CalculationTasksTest {

  private static final TestTarget TARGET1 = new TestTarget();
  private static final TestTarget TARGET2 = new TestTarget();
  private static final Measure MEASURE1 = Measure.of("PV");
  private static final Measure MEASURE2 = Measure.of("PV2");
  private static final PricingRules PRICING_RULES = PricingRules.empty();
  private static final MarketDataMappings MD_MAPPINGS = MarketDataMappings.of(MarketDataFeed.NONE);
  private static final MarketDataRules MD_RULES = MarketDataRules.of(MarketDataRule.anyTarget(MD_MAPPINGS));
  private static final ReportingRules REPORTING_RULES = ReportingRules.fixedCurrency(Currency.USD);

  //-------------------------------------------------------------------------
  public void createCalculationConfig() {
    MarketDataMappings marketDataMappings = DefaultMarketDataMappings.builder()
        .marketDataFeed(MarketDataFeed.of("MarketDataFeed"))
        .build();

    MarketDataRules marketDataRules = MarketDataRules.of(MarketDataRule.of(marketDataMappings, TestTarget.class));
    DefaultFunctionGroup<TestTarget> functionGroup =
        DefaultFunctionGroup.builder(TestTarget.class)
            .name("DefaultGroup")
            .addFunction(MEASURE1, TestFunction.class)
            .build();
    PricingRule<TestTarget> pricingRule = PricingRule.builder(TestTarget.class)
        .functionGroup(functionGroup)
        .addMeasures(MEASURE1)
        .build();

    DefaultPricingRules pricingRules = DefaultPricingRules.of(pricingRule);
    List<TestTarget> targets = ImmutableList.of(TARGET1, TARGET2);
    List<Column> columns = ImmutableList.of(Column.of(MEASURE1), Column.of(MEASURE2));
    CalculationRules calculationRules = CalculationRules.of(pricingRules, marketDataRules, REPORTING_RULES);

    CalculationTasks test = CalculationTasks.of(calculationRules, targets, columns);
    assertThat(test.getTargets()).hasSize(2);
    assertThat(test.getTargets()).containsExactly(TARGET1, TARGET2);
    assertThat(test.getColumns()).hasSize(2);
    assertThat(test.getColumns()).containsExactly(Column.of(MEASURE1), Column.of(MEASURE2));
    assertThat(test.getTasks()).hasSize(4);
    assertThat(test.getTasks().get(0).getTarget()).isEqualTo(TARGET1);
    assertThat(test.getTasks().get(1).getTarget()).isEqualTo(TARGET1);
    assertThat(test.getTasks().get(2).getTarget()).isEqualTo(TARGET2);
    assertThat(test.getTasks().get(3).getTarget()).isEqualTo(TARGET2);
  }

  //-------------------------------------------------------------------------
  public void noMatchingMarketDataRules() {
    DefaultFunctionGroup<TestTarget> functionGroup = DefaultFunctionGroup.builder(TestTarget.class)
        .name("DefaultGroup")
        .addFunction(MEASURE1, TestFunction.class)
        .build();
    PricingRule<TestTarget> pricingRule = PricingRule.builder(TestTarget.class)
        .functionGroup(functionGroup)
        .addMeasures(MEASURE1)
        .build();
    DefaultPricingRules pricingRules = DefaultPricingRules.of(pricingRule);
    CalculationRules calculationRules = CalculationRules.of(pricingRules, MarketDataRules.empty(), REPORTING_RULES);
    List<TestTarget> targets = ImmutableList.of(TARGET1);
    List<Column> columns = ImmutableList.of(Column.of(MEASURE1));

    CalculationTasks test = CalculationTasks.of(calculationRules, targets, columns);

    MarketDataRequirements requirements = test.getRequirements();
    Set<? extends MarketDataId<?>> nonObservables = requirements.getNonObservables();
    ImmutableSet<? extends ObservableId> observables = requirements.getObservables();
    ImmutableSet<ObservableId> timeSeries = requirements.getTimeSeries();

    NoMatchingRuleId nonObservableId = NoMatchingRuleId.of(TestKey.of("1"));
    assertThat(nonObservables).hasSize(1);
    assertThat(nonObservables.iterator().next()).isEqualTo(nonObservableId);

    MarketDataId<?> observableId = TestObservableKey.of("2").toMarketDataId(MarketDataFeed.NO_RULE);
    assertThat(observables).hasSize(1);
    assertThat(observables.iterator().next()).isEqualTo(observableId);

    MarketDataId<?> timeSeriesId = TestObservableKey.of("3").toMarketDataId(MarketDataFeed.NO_RULE);
    assertThat(timeSeries).hasSize(1);
    assertThat(timeSeries.iterator().next()).isEqualTo(timeSeriesId);
  }

  //-------------------------------------------------------------------------
  public void testToString() {
    List<TestTarget> targets = ImmutableList.of(TARGET1, TARGET1);
    List<Column> columns = ImmutableList.of(Column.of(MEASURE1), Column.of(MEASURE1), Column.of(MEASURE1));
    CalculationRules rules = CalculationRules.of(PRICING_RULES, MD_RULES, REPORTING_RULES);
    CalculationTasks task = CalculationTasks.of(rules, targets, columns);
    assertThat(task.toString()).isEqualTo("CalculationTasks[grid=2x3]");
  }

  //-------------------------------------------------------------------------
  private static class TestTarget implements CalculationTarget {

  }

  /**
   * Function that returns a value that is not currency convertible.
   */
  public static final class TestFunction implements CalculationSingleFunction<TestTarget, Object> {

    @Override
    public FunctionRequirements requirements(TestTarget target) {
      return FunctionRequirements.builder()
          .singleValueRequirements(
              ImmutableSet.of(
                  TestKey.of("1"),
                  TestObservableKey.of("2")))
          .timeSeriesRequirements(TestObservableKey.of("3"))
          .build();
    }

    @Override
    public Object execute(TestTarget target, CalculationMarketData marketData) {
      return "bar";
    }
  }

}
