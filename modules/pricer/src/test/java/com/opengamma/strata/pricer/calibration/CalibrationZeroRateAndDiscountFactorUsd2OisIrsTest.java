/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.calibration;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M;
import static com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions.USD_FIXED_1Y_FED_FUND_OIS;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.Trade;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.market.ImmutableMarketData;
import com.opengamma.strata.basics.market.ImmutableMarketDataBuilder;
import com.opengamma.strata.basics.market.MarketData;
import com.opengamma.strata.basics.market.MarketDataKey;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveNode;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurveDefinition;
import com.opengamma.strata.market.curve.node.FixedIborSwapCurveNode;
import com.opengamma.strata.market.curve.node.FixedOvernightSwapCurveNode;
import com.opengamma.strata.market.curve.node.FraCurveNode;
import com.opengamma.strata.market.curve.node.IborFixingDepositCurveNode;
import com.opengamma.strata.market.interpolator.CurveExtrapolator;
import com.opengamma.strata.market.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.interpolator.CurveInterpolator;
import com.opengamma.strata.market.interpolator.CurveInterpolators;
import com.opengamma.strata.market.key.QuoteKey;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.deposit.DiscountingIborFixingDepositProductPricer;
import com.opengamma.strata.pricer.fra.DiscountingFraTradePricer;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.deposit.IborFixingDepositTrade;
import com.opengamma.strata.product.deposit.type.IborFixingDepositTemplate;
import com.opengamma.strata.product.fra.FraTrade;
import com.opengamma.strata.product.fra.type.FraTemplate;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.product.swap.type.FixedIborSwapTemplate;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapTemplate;

/**
 * Test for curve calibration with 2 curves in USD.
 * One curve is Discounting and Fed Fund forward and the other one is Libor 3M forward.
 */
@Test
public class CalibrationZeroRateAndDiscountFactorUsd2OisIrsTest {

  private static final LocalDate VAL_DATE_BD = LocalDate.of(2015, 7, 21);
  private static final LocalDate VAL_DATE_HO = LocalDate.of(2015, 12, 25);

  private static final CurveInterpolator INTERPOLATOR_LINEAR = CurveInterpolators.LINEAR;
  private static final CurveExtrapolator EXTRAPOLATOR_FLAT = CurveExtrapolators.FLAT;
  private static final DayCount CURVE_DC = ACT_365F;
  private static final LocalDateDoubleTimeSeries TS_EMTPY = LocalDateDoubleTimeSeries.empty();

  private static final String SCHEME = "CALIBRATION";

  /** Curve names */
  private static final String DSCON_NAME = "USD-DSCON-OIS";
  private static final CurveName DSCON_CURVE_NAME = CurveName.of(DSCON_NAME);
  private static final String FWD3_NAME = "USD-LIBOR3M-FRAIRS";
  private static final CurveName FWD3_CURVE_NAME = CurveName.of(FWD3_NAME);
  /** Curves associations to currencies and indices. */
  private static final Map<CurveName, Currency> DSC_NAMES = new HashMap<>();
  private static final Map<CurveName, Set<Index>> IDX_NAMES = new HashMap<>();
  private static final Map<Index, LocalDateDoubleTimeSeries> TS_EMPTY = new HashMap<>();
  private static final Map<Index, LocalDateDoubleTimeSeries> TS_BD_LIBOR3M = new HashMap<>();
  private static final Map<Index, LocalDateDoubleTimeSeries> TS_HO_LIBOR3M = new HashMap<>();
  static {
    DSC_NAMES.put(DSCON_CURVE_NAME, USD);
    Set<Index> usdFedFundSet = new HashSet<>();
    usdFedFundSet.add(USD_FED_FUND);
    IDX_NAMES.put(DSCON_CURVE_NAME, usdFedFundSet);
    Set<Index> usdLibor3Set = new HashSet<>();
    usdLibor3Set.add(USD_LIBOR_3M);
    IDX_NAMES.put(FWD3_CURVE_NAME, usdLibor3Set);
    double fixingValue = 0.002345;
    LocalDateDoubleTimeSeries tsBdUsdLibor3M = 
        LocalDateDoubleTimeSeries.builder().put(VAL_DATE_BD, fixingValue).build();
    TS_BD_LIBOR3M.put(USD_LIBOR_3M, tsBdUsdLibor3M);
    TS_BD_LIBOR3M.put(USD_FED_FUND, TS_EMTPY);
    LocalDate fixingDateHo = LocalDate.of(2015, 12, 24);
    LocalDateDoubleTimeSeries tsHoUsdLibor3M = 
        LocalDateDoubleTimeSeries.builder().put(fixingDateHo, fixingValue).build();
    TS_HO_LIBOR3M.put(USD_LIBOR_3M, tsHoUsdLibor3M);
    TS_HO_LIBOR3M.put(USD_FED_FUND, TS_EMTPY);
    TS_EMPTY.put(USD_LIBOR_3M, TS_EMTPY);
    TS_EMPTY.put(USD_FED_FUND, TS_EMTPY);
  }

  /** Data for USD-DSCON curve */
  /* Market values */
  private static final double[] DSC_MARKET_QUOTES = new double[] {
      0.00072000, 0.00082000, 0.00093000, 0.00090000, 0.00105000,
      0.00118500, 0.00318650, 0.00318650, 0.00704000, 0.01121500, 0.01515000,
      0.01845500, 0.02111000, 0.02332000, 0.02513500, 0.02668500};
  private static final int DSC_NB_NODES = DSC_MARKET_QUOTES.length;
  private static final String[] DSC_ID_VALUE = new String[] {
      "OIS1M", "OIS2M", "OIS3M", "OIS6M", "OIS9M",
      "OIS1Y", "OIS18M", "OIS2Y", "OIS3Y", "OIS4Y", "OIS5Y",
      "OIS6Y", "OIS7Y", "OIS8Y", "OIS9Y", "OIS10Y"};
  /* Nodes */
  private static final CurveNode[] DSC_NODES = new CurveNode[DSC_NB_NODES];
  /* Tenors */
  private static final Period[] DSC_OIS_TENORS = new Period[] {
      Period.ofMonths(1), Period.ofMonths(2), Period.ofMonths(3), Period.ofMonths(6), Period.ofMonths(9),
      Period.ofYears(1), Period.ofMonths(18), Period.ofYears(2), Period.ofYears(3), Period.ofYears(4), Period.ofYears(5),
      Period.ofYears(6), Period.ofYears(7), Period.ofYears(8), Period.ofYears(9), Period.ofYears(10)};
  private static final int DSC_NB_OIS_NODES = DSC_OIS_TENORS.length;
  static {
    for (int i = 0; i < DSC_NB_OIS_NODES; i++) {
      DSC_NODES[i] = FixedOvernightSwapCurveNode.of(
          FixedOvernightSwapTemplate.of(Period.ZERO, Tenor.of(DSC_OIS_TENORS[i]), USD_FIXED_1Y_FED_FUND_OIS),
          QuoteKey.of(StandardId.of(SCHEME, DSC_ID_VALUE[i])));
    }
  }

  /** Data for USD-LIBOR3M curve */
  /* Market values */
  private static final double[] FWD3_MARKET_QUOTES = new double[] {
      0.00236600,
      0.00258250, 0.00296050,
      0.00294300, 0.00503000, 0.00939150, 0.01380800, 0.01732000,
      0.02396200, 0.02930000, 0.03195000, 0.03423500, 0.03615500,
      0.03696850, 0.03734500};
  private static final int FWD3_NB_NODES = FWD3_MARKET_QUOTES.length;
  private static final String[] FWD3_ID_VALUE = new String[] {
      "Fixing",
      "FRA3Mx6M", "FRA6Mx9M",
      "IRS1Y", "IRS2Y", "IRS3Y", "IRS4Y", "IRS5Y",
      "IRS7Y", "IRS10Y", "IRS12Y", "IRS15Y", "IRS20Y",
      "IRS25Y", "IRS30Y"};
  /* Nodes */
  private static final CurveNode[] FWD3_NODES = new CurveNode[FWD3_NB_NODES];
  /* Tenors */
  private static final Period[] FWD3_FRA_TENORS = new Period[] { // Period to start
      Period.ofMonths(3), Period.ofMonths(6)};
  private static final int FWD3_NB_FRA_NODES = FWD3_FRA_TENORS.length;
  private static final Period[] FWD3_IRS_TENORS = new Period[] {
      Period.ofYears(1), Period.ofYears(2), Period.ofYears(3), Period.ofYears(4), Period.ofYears(5),
      Period.ofYears(7), Period.ofYears(10), Period.ofYears(12), Period.ofYears(15), Period.ofYears(20),
      Period.ofYears(25), Period.ofYears(30)};
  private static final int FWD3_NB_IRS_NODES = FWD3_IRS_TENORS.length;
  static {
    FWD3_NODES[0] = IborFixingDepositCurveNode.of(IborFixingDepositTemplate.of(USD_LIBOR_3M),
        QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[0])));
    for (int i = 0; i < FWD3_NB_FRA_NODES; i++) {
      FWD3_NODES[i + 1] = FraCurveNode.of(FraTemplate.of(FWD3_FRA_TENORS[i], USD_LIBOR_3M),
          QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i + 1])));
    }
    for (int i = 0; i < FWD3_NB_IRS_NODES; i++) {
      FWD3_NODES[i + 1 + FWD3_NB_FRA_NODES] = FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Period.ZERO, Tenor.of(FWD3_IRS_TENORS[i]), USD_FIXED_6M_LIBOR_3M),
          QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i + 1 + FWD3_NB_FRA_NODES])));
    }
  }

  /** All quotes for the curve calibration on good business day. */
  private static final ImmutableMarketData ALL_QUOTES_BD;
  static {
    ImmutableMarketDataBuilder builder = ImmutableMarketData.builder(VAL_DATE_BD);
    for (int i = 0; i < FWD3_NB_NODES; i++) {
      builder.addValue(QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i])), FWD3_MARKET_QUOTES[i]);
    }
    for (int i = 0; i < DSC_NB_NODES; i++) {
      builder.addValue(QuoteKey.of(StandardId.of(SCHEME, DSC_ID_VALUE[i])), DSC_MARKET_QUOTES[i]);
    }
    ALL_QUOTES_BD = builder.build();
  }
  
  /** All quotes for the curve calibration on holiday. */
  private static final ImmutableMarketData ALL_QUOTES_HO = ALL_QUOTES_BD.toBuilder().valuationDate(VAL_DATE_HO).build();

  /** All nodes by groups. */
  private static final List<List<CurveNode[]>> CURVES_NODES = new ArrayList<>();
  static {
    List<CurveNode[]> groupDsc = new ArrayList<>();
    groupDsc.add(DSC_NODES);
    CURVES_NODES.add(groupDsc);
    List<CurveNode[]> groupFwd3 = new ArrayList<>();
    groupFwd3.add(FWD3_NODES);
    CURVES_NODES.add(groupFwd3);
  }

  /** All metadata by groups */
  private static final List<List<CurveMetadata>> CURVES_METADATA = new ArrayList<>();
  static {
    List<CurveMetadata> groupDsc = new ArrayList<>();
    groupDsc.add(DefaultCurveMetadata.builder().curveName(DSCON_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    CURVES_METADATA.add(groupDsc);
    List<CurveMetadata> groupFwd3 = new ArrayList<>();
    groupFwd3.add(DefaultCurveMetadata.builder().curveName(FWD3_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    CURVES_METADATA.add(groupFwd3);
  }

  private static final DiscountingIborFixingDepositProductPricer FIXING_PRICER =
      DiscountingIborFixingDepositProductPricer.DEFAULT;
  private static final DiscountingFraTradePricer FRA_PRICER =
      DiscountingFraTradePricer.DEFAULT;
  private static final DiscountingSwapProductPricer SWAP_PRICER =
      DiscountingSwapProductPricer.DEFAULT;
  private static final MarketQuoteSensitivityCalculator MQC = MarketQuoteSensitivityCalculator.DEFAULT;

  private static final CalibrationMeasures CALIBRATION_MEASURES = CalibrationMeasures.DEFAULT;
  private static final CurveCalibrator CALIBRATOR = CurveCalibrator.of(1e-9, 1e-9, 100, CALIBRATION_MEASURES);

  // Constants
  private static final double TOLERANCE_PV = 1.0E-6;
  private static final double TOLERANCE_PV_DELTA = 1.0E+2;

  private static final CurveGroupName CURVE_GROUP_NAME = CurveGroupName.of("USD-DSCON-LIBOR3M");
  private static final InterpolatedNodalCurveDefinition DSC_CURVE_DEFN =
      InterpolatedNodalCurveDefinition.builder()
          .name(DSCON_CURVE_NAME)
          .xValueType(ValueType.YEAR_FRACTION)
          .yValueType(ValueType.ZERO_RATE)
          .dayCount(CURVE_DC)
          .interpolator(INTERPOLATOR_LINEAR)
          .extrapolatorLeft(EXTRAPOLATOR_FLAT)
          .extrapolatorRight(EXTRAPOLATOR_FLAT)
          .nodes(DSC_NODES).build();
  private static final InterpolatedNodalCurveDefinition FWD3_CURVE_DEFN =
      InterpolatedNodalCurveDefinition.builder()
          .name(FWD3_CURVE_NAME)
          .xValueType(ValueType.YEAR_FRACTION)
          .yValueType(ValueType.ZERO_RATE)
          .dayCount(CURVE_DC)
          .interpolator(INTERPOLATOR_LINEAR)
          .extrapolatorLeft(EXTRAPOLATOR_FLAT)
          .extrapolatorRight(EXTRAPOLATOR_FLAT)
          .nodes(FWD3_NODES).build();
  private static final CurveGroupDefinition CURVE_GROUP_CONFIG =
      CurveGroupDefinition.builder()
          .name(CURVE_GROUP_NAME)
          .addCurve(DSC_CURVE_DEFN, USD, USD_FED_FUND)
          .addForwardCurve(FWD3_CURVE_DEFN, USD_LIBOR_3M).build();

  private static final CurveGroupDefinition GROUP_1 =
      CurveGroupDefinition.builder()
          .name(CurveGroupName.of("USD-DSCON"))
          .addCurve(DSC_CURVE_DEFN, USD, USD_FED_FUND)
          .build();
  private static final CurveGroupDefinition GROUP_2 =
      CurveGroupDefinition.builder()
          .name(CurveGroupName.of("USD-LIBOR3M"))
          .addForwardCurve(FWD3_CURVE_DEFN, USD_LIBOR_3M)
          .build();
  private static final ImmutableRatesProvider KNOWN_DATA = ImmutableRatesProvider.builder(VAL_DATE_BD).build();

  //-------------------------------------------------------------------------
  public void calibration_present_value_oneGroup_no_fixing() {
    ImmutableRatesProvider result =
        CALIBRATOR.calibrate(CURVE_GROUP_CONFIG, VAL_DATE_BD, ALL_QUOTES_BD, TS_EMPTY);
    assertResult(result, VAL_DATE_BD);
  }
  
  public void calibration_present_value_oneGroup_fixing() {
    ImmutableRatesProvider result =
        CALIBRATOR.calibrate(CURVE_GROUP_CONFIG, VAL_DATE_BD, ALL_QUOTES_BD, TS_BD_LIBOR3M);
    assertResult(result, VAL_DATE_BD);
  }
  
  public void calibration_present_value_oneGroup_holiday() {
    ImmutableRatesProvider result =
        CALIBRATOR.calibrate(CURVE_GROUP_CONFIG, VAL_DATE_HO, ALL_QUOTES_HO, TS_HO_LIBOR3M);
    assertResult(result, VAL_DATE_HO);
  }

  public void calibration_present_value_twoGroups() {
    ImmutableRatesProvider result =
        CALIBRATOR.calibrate(ImmutableList.of(GROUP_1, GROUP_2), KNOWN_DATA, ALL_QUOTES_BD);
    assertResult(result, VAL_DATE_BD);
  }

  private void assertResult(ImmutableRatesProvider result, LocalDate valDate) {
    // Test PV Dsc
    CurveNode[] dscNodes = CURVES_NODES.get(0).get(0);
    List<Trade> dscTrades = new ArrayList<>();
    for (int i = 0; i < dscNodes.length; i++) {
      dscTrades.add(dscNodes[i].trade(valDate, ALL_QUOTES_BD));
    }
    // OIS
    for (int i = 0; i < DSC_NB_OIS_NODES; i++) {
      MultiCurrencyAmount pvIrs = SWAP_PRICER
          .presentValue(((SwapTrade) dscTrades.get(i)).getProduct(), result);
      assertEquals(pvIrs.getAmount(USD).getAmount(), 0.0, TOLERANCE_PV);
    }
    // Test PV Fwd3
    CurveNode[] fwd3Nodes = CURVES_NODES.get(1).get(0);
    List<Trade> fwd3Trades = new ArrayList<>();
    for (int i = 0; i < fwd3Nodes.length; i++) {
      fwd3Trades.add(fwd3Nodes[i].trade(valDate, ALL_QUOTES_BD));
    }
    // Fixing 
    CurrencyAmount pvFixing =
        FIXING_PRICER.presentValue(((IborFixingDepositTrade) fwd3Trades.get(0)).getProduct(), result);
    assertEquals(pvFixing.getAmount(), 0.0, TOLERANCE_PV);
    // FRA
    for (int i = 0; i < FWD3_NB_FRA_NODES; i++) {
      CurrencyAmount pvFra =
          FRA_PRICER.presentValue(((FraTrade) fwd3Trades.get(i + 1)), result);
      assertEquals(pvFra.getAmount(), 0.0, TOLERANCE_PV);
    }
    // IRS
    for (int i = 0; i < FWD3_NB_IRS_NODES; i++) {
      MultiCurrencyAmount pvIrs = SWAP_PRICER
          .presentValue(((SwapTrade) fwd3Trades.get(i + 1 + FWD3_NB_FRA_NODES)).getProduct(), result);
      assertEquals(pvIrs.getAmount(USD).getAmount(), 0.0, TOLERANCE_PV);
    }
  }

  public void calibration_market_quote_sensitivity_one_group_no_fixing() {
    double shift = 1.0E-6;
    Function<MarketData, ImmutableRatesProvider> f =
        marketData -> CALIBRATOR.calibrate(CURVE_GROUP_CONFIG, VAL_DATE_BD, marketData, TS_EMPTY);
    calibration_market_quote_sensitivity_check(f, CURVE_GROUP_CONFIG, shift, TS_EMPTY);
  }

  public void calibration_market_quote_sensitivity_one_group_fixing() {
    double shift = 1.0E-6;
    Function<MarketData, ImmutableRatesProvider> f =
        marketData -> CALIBRATOR.calibrate(CURVE_GROUP_CONFIG, VAL_DATE_BD, marketData, TS_BD_LIBOR3M);
    calibration_market_quote_sensitivity_check(f, CURVE_GROUP_CONFIG, shift, TS_BD_LIBOR3M);
  }

  public void calibration_market_quote_sensitivity_two_group() {
    double shift = 1.0E-6;
    Function<MarketData, ImmutableRatesProvider> calibrator =
        marketData -> CALIBRATOR.calibrate(ImmutableList.of(GROUP_1, GROUP_2), KNOWN_DATA, marketData);
    calibration_market_quote_sensitivity_check(calibrator, CURVE_GROUP_CONFIG, shift, TS_EMPTY);
  }

  private void calibration_market_quote_sensitivity_check(
      Function<MarketData, ImmutableRatesProvider> calibrator,
      CurveGroupDefinition config,
      double shift,
      Map<Index, LocalDateDoubleTimeSeries> ts) {
    double notional = 100_000_000.0;
    double rate = 0.0400;
    SwapTrade trade = FixedIborSwapConventions.USD_FIXED_1Y_LIBOR_3M.toTrade(VAL_DATE_BD, Period.ofMonths(6),
        Tenor.TENOR_7Y, BuySell.BUY, notional, rate);
    ImmutableRatesProvider result =
        CALIBRATOR.calibrate(config, VAL_DATE_BD, ALL_QUOTES_BD, ts);
    PointSensitivityBuilder pts = SWAP_PRICER.presentValueSensitivity(trade.getProduct(), result);
    CurveCurrencyParameterSensitivities ps = result.curveParameterSensitivity(pts.build());
    CurveCurrencyParameterSensitivities mqs = MQC.sensitivity(ps, result);
    double pv0 = SWAP_PRICER.presentValue(trade.getProduct(), result).getAmount(USD).getAmount();
    double[] mqsDscComputed = mqs.getSensitivity(DSCON_CURVE_NAME, USD).getSensitivity().toArray();
    for (int i = 0; i < DSC_NB_NODES; i++) {
      Map<MarketDataKey<?>, Object> map = new HashMap<>(ALL_QUOTES_BD.getValues());
      map.put(QuoteKey.of(StandardId.of(SCHEME, DSC_ID_VALUE[i])), DSC_MARKET_QUOTES[i] + shift);
      ImmutableMarketData marketData = ImmutableMarketData.of(VAL_DATE_BD, map);
      ImmutableRatesProvider rpShifted = calibrator.apply(marketData);
      double pvS = SWAP_PRICER.presentValue(trade.getProduct(), rpShifted).getAmount(USD).getAmount();
      assertEquals(mqsDscComputed[i], (pvS - pv0) / shift, TOLERANCE_PV_DELTA);
    }
    double[] mqsFwd3Computed = mqs.getSensitivity(FWD3_CURVE_NAME, USD).getSensitivity().toArray();
    for (int i = 0; i < FWD3_NB_NODES; i++) {
      Map<MarketDataKey<?>, Object> map = new HashMap<>(ALL_QUOTES_BD.getValues());
      map.put(QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i])), FWD3_MARKET_QUOTES[i] + shift);
      ImmutableMarketData marketData = ImmutableMarketData.of(VAL_DATE_BD, map);
      ImmutableRatesProvider rpShifted = calibrator.apply(marketData);
      double pvS = SWAP_PRICER.presentValue(trade.getProduct(), rpShifted).getAmount(USD).getAmount();
      assertEquals(mqsFwd3Computed[i], (pvS - pv0) / shift, TOLERANCE_PV_DELTA);
    }
  }

  public void calibration_present_value_discountCurve() {
    CurveInterpolator interp = CurveInterpolators.LOG_LINEAR;
    CurveExtrapolator extrapRight = CurveExtrapolators.LOG_LINEAR;
    CurveExtrapolator extrapLeft = CurveExtrapolators.QUADRATIC_LEFT;
    InterpolatedNodalCurveDefinition dsc =
        InterpolatedNodalCurveDefinition.builder()
            .name(DSCON_CURVE_NAME)
            .xValueType(ValueType.YEAR_FRACTION)
            .yValueType(ValueType.DISCOUNT_FACTOR)
            .dayCount(CURVE_DC)
            .interpolator(interp)
            .extrapolatorLeft(extrapLeft)
            .extrapolatorRight(extrapRight)
            .nodes(DSC_NODES).build();
    InterpolatedNodalCurveDefinition fwd =
        InterpolatedNodalCurveDefinition.builder()
            .name(FWD3_CURVE_NAME)
            .xValueType(ValueType.YEAR_FRACTION)
            .yValueType(ValueType.DISCOUNT_FACTOR)
            .dayCount(CURVE_DC)
            .interpolator(interp)
            .extrapolatorLeft(extrapLeft)
            .extrapolatorRight(extrapRight)
            .nodes(FWD3_NODES).build();
    CurveGroupDefinition config =
        CurveGroupDefinition.builder()
            .name(CURVE_GROUP_NAME)
            .addCurve(dsc, USD, USD_FED_FUND)
            .addForwardCurve(fwd, USD_LIBOR_3M)
            .build();
    ImmutableRatesProvider result =
        CALIBRATOR.calibrate(config, VAL_DATE_BD, ALL_QUOTES_BD, TS_EMPTY);
    assertResult(result, VAL_DATE_BD);

    double shift = 1.0E-6;
    Function<MarketData, ImmutableRatesProvider> f =
        marketData -> CALIBRATOR.calibrate(config, VAL_DATE_BD, marketData, TS_EMPTY);
    calibration_market_quote_sensitivity_check(f, config, shift, TS_EMPTY);
  }

  public void calibration_present_value_discountCurve_clamped() {
    CurveInterpolator interp = CurveInterpolators.LOG_NATURAL_CUBIC_DISCOUNT_FACTOR;
    CurveExtrapolator extrapRight = CurveExtrapolators.LOG_LINEAR;
    CurveExtrapolator extrapLeft = CurveExtrapolators.INTERPOLATOR;
    InterpolatedNodalCurveDefinition dsc =
        InterpolatedNodalCurveDefinition.builder()
            .name(DSCON_CURVE_NAME)
            .xValueType(ValueType.YEAR_FRACTION)
            .yValueType(ValueType.DISCOUNT_FACTOR)
            .dayCount(CURVE_DC)
            .interpolator(interp)
            .extrapolatorLeft(extrapLeft)
            .extrapolatorRight(extrapRight)
            .nodes(DSC_NODES).build();
    InterpolatedNodalCurveDefinition fwd =
        InterpolatedNodalCurveDefinition.builder()
            .name(FWD3_CURVE_NAME)
            .xValueType(ValueType.YEAR_FRACTION)
            .yValueType(ValueType.DISCOUNT_FACTOR)
            .dayCount(CURVE_DC)
            .interpolator(interp)
            .extrapolatorLeft(extrapLeft)
            .extrapolatorRight(extrapRight)
            .nodes(FWD3_NODES).build();
    CurveGroupDefinition config =
        CurveGroupDefinition.builder()
            .name(CURVE_GROUP_NAME)
            .addCurve(dsc, USD, USD_FED_FUND)
            .addForwardCurve(fwd, USD_LIBOR_3M)
            .build();
    ImmutableRatesProvider result =
        CALIBRATOR.calibrate(config, VAL_DATE_BD, ALL_QUOTES_BD, TS_EMPTY);
    assertResult(result, VAL_DATE_BD);

    double shift = 1.0E-6;
    Function<MarketData, ImmutableRatesProvider> f =
        marketData -> CALIBRATOR.calibrate(config, VAL_DATE_BD, marketData, TS_EMPTY);
    calibration_market_quote_sensitivity_check(f, config, shift, TS_EMPTY);
  }

  //-------------------------------------------------------------------------
  @SuppressWarnings("unused")
  @Test(enabled = false)
  void performance() {
    long startTime, endTime;
    int nbTests = 100;
    int nbRep = 3;
    int count = 0;

    for (int i = 0; i < nbRep; i++) {
      startTime = System.currentTimeMillis();
      for (int looprep = 0; looprep < nbTests; looprep++) {
        ImmutableRatesProvider result =
            CALIBRATOR.calibrate(CURVE_GROUP_CONFIG, VAL_DATE_BD, ALL_QUOTES_BD, TS_EMPTY);
        count += result.getDiscountCurves().size() + result.getIndexCurves().size();
      }
      endTime = System.currentTimeMillis();
      System.out.println("Performance: " + nbTests + " calibrations for 2 curve with 30 nodes in "
          + (endTime - startTime) + " ms.");
    }
    System.out.println("Avoiding hotspot: " + count);
    // Previous run: 1600 ms for 100 calibrations (2 curve simultaneous - 30 nodes)
  }

}