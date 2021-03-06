/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calc.runner;

import static com.opengamma.strata.collect.CollectProjectAssertions.assertThat;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.date;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.opengamma.strata.basics.CalculationTarget;
import com.opengamma.strata.basics.market.TestObservableKey;
import com.opengamma.strata.calc.Column;
import com.opengamma.strata.calc.config.Measure;
import com.opengamma.strata.calc.config.ReportingRules;
import com.opengamma.strata.calc.marketdata.CalculationEnvironment;
import com.opengamma.strata.calc.marketdata.CalculationMarketData;
import com.opengamma.strata.calc.marketdata.FunctionRequirements;
import com.opengamma.strata.calc.marketdata.MarketEnvironment;
import com.opengamma.strata.calc.marketdata.TestKey;
import com.opengamma.strata.calc.marketdata.mapping.MarketDataMappings;
import com.opengamma.strata.calc.runner.function.CalculationSingleFunction;
import com.opengamma.strata.calc.runner.function.result.DefaultScenarioResult;
import com.opengamma.strata.calc.runner.function.result.ScenarioResult;
import com.opengamma.strata.collect.result.Result;

/**
 * Test {@link CalculationTaskRunner} and {@link DefaultCalculationTaskRunner}.
 */
@Test
public class DefaultCalculationTaskRunnerTest {

  private static final TestTarget TARGET = new TestTarget();
  private static final Measure MEASURE = Measure.of("PV");
  private static final LocalDate VAL_DATE = date(2011, 3, 8);

  //-------------------------------------------------------------------------
  /**
   * Test that ScenarioResults containing a single value are unwrapped when calling calculate() with BaseMarketData.
   */
  public void unwrapScenarioResults() {
    DefaultScenarioResult<String> scenarioResult = DefaultScenarioResult.of("foo");
    ScenarioResultFunction fn = new ScenarioResultFunction(scenarioResult);
    CalculationTask task = CalculationTask.of(TARGET, MEASURE, 0, 0, fn, MarketDataMappings.empty(), ReportingRules.empty());
    Column column = Column.of(Measure.PRESENT_VALUE);
    CalculationTasks tasks = CalculationTasks.of(ImmutableList.of(task), ImmutableList.of(column));

    // using the direct executor means there is no need to close/shutdown the runner
    CalculationTaskRunner test = CalculationTaskRunner.of(MoreExecutors.newDirectExecutorService());

    CalculationEnvironment marketData = MarketEnvironment.builder().valuationDate(VAL_DATE).build();
    Results results1 = test.calculateSingleScenario(tasks, marketData);
    Result<?> result1 = results1.get(0, 0);
    // Check the result contains the string directly, not the result wrapping the string
    assertThat(result1).hasValue("foo");

    CalculationEnvironment scenarioMarketData = MarketEnvironment.builder().valuationDate(VAL_DATE).build();
    Results results2 = test.calculateMultipleScenarios(tasks, scenarioMarketData);
    Result<?> result2 = results2.get(0, 0);
    // Check the result contains the scenario result wrapping the string
    assertThat(result2).hasValue(scenarioResult);
  }

  /**
   * Test that ScenarioResults containing multiple values are an error.
   */
  public void unwrapMultipleScenarioResults() {
    DefaultScenarioResult<String> scenarioResult = DefaultScenarioResult.of("foo", "bar");
    ScenarioResultFunction fn = new ScenarioResultFunction(scenarioResult);
    CalculationTask task = CalculationTask.of(TARGET, MEASURE, 0, 0, fn, MarketDataMappings.empty(), ReportingRules.empty());
    Column column = Column.of(Measure.PRESENT_VALUE);
    CalculationTasks tasks = CalculationTasks.of(ImmutableList.of(task), ImmutableList.of(column));

    // using the direct executor means there is no need to close/shutdown the runner
    CalculationTaskRunner test = CalculationTaskRunner.of(MoreExecutors.newDirectExecutorService());

    CalculationEnvironment marketData = MarketEnvironment.builder().valuationDate(VAL_DATE).build();
    assertThrowsIllegalArg(() -> test.calculateSingleScenario(tasks, marketData));
  }

  /**
   * Test that ScenarioResults containing a single value are unwrapped when calling calculateAsync() with BaseMarketData.
   */
  public void unwrapScenarioResultsAsync() {
    DefaultScenarioResult<String> scenarioResult = DefaultScenarioResult.of("foo");
    ScenarioResultFunction fn = new ScenarioResultFunction(scenarioResult);
    CalculationTask task = CalculationTask.of(TARGET, MEASURE, 0, 0, fn, MarketDataMappings.empty(), ReportingRules.empty());
    Column column = Column.of(Measure.PRESENT_VALUE);
    CalculationTasks tasks = CalculationTasks.of(ImmutableList.of(task), ImmutableList.of(column));

    // using the direct executor means there is no need to close/shutdown the runner
    CalculationTaskRunner test = CalculationTaskRunner.of(MoreExecutors.newDirectExecutorService());
    Listener listener = new Listener();

    CalculationEnvironment marketData = MarketEnvironment.builder().valuationDate(VAL_DATE).build();
    test.calculateSingleScenarioAsync(tasks, marketData, listener);
    CalculationResult calculationResult1 = listener.result;
    Result<?> result1 = calculationResult1.getResult();
    // Check the result contains the string directly, not the result wrapping the string
    assertThat(result1).hasValue("foo");

    CalculationEnvironment scenarioMarketData = MarketEnvironment.builder().valuationDate(VAL_DATE).build();
    test.calculateMultipleScenariosAsync(tasks, scenarioMarketData, listener);
    CalculationResult calculationResult2 = listener.result;
    Result<?> result2 = calculationResult2.getResult();
    // Check the result contains the scenario result wrapping the string
    assertThat(result2).hasValue(scenarioResult);
  }

  //-------------------------------------------------------------------------
  private static class TestTarget implements CalculationTarget { }

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

  private static final class ScenarioResultFunction
      implements CalculationSingleFunction<TestTarget, ScenarioResult<String>> {

    private final ScenarioResult<String> result;

    private ScenarioResultFunction(ScenarioResult<String> result) {
      this.result = result;
    }

    @Override
    public ScenarioResult<String> execute(TestTarget target, CalculationMarketData marketData) {
      return result;
    }

    @Override
    public FunctionRequirements requirements(TestTarget target) {
      return FunctionRequirements.empty();
    }
  }

  private static final class Listener implements CalculationListener {

    private CalculationResult result;

    @Override
    public void resultReceived(CalculationResult result) {
      this.result = result;
    }

    @Override
    public void calculationsComplete() {
      // Do nothing
    }
  }
}
