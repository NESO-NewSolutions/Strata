/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.index;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.BusinessDayConventions.FOLLOWING;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.BusinessDayConventions.PRECEDING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_360;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.date.HolidayCalendars.EUTA;
import static com.opengamma.strata.basics.date.HolidayCalendars.GBLO;
import static com.opengamma.strata.basics.date.HolidayCalendars.USNY;
import static com.opengamma.strata.basics.date.Tenor.TENOR_3M;
import static com.opengamma.strata.collect.TestHelper.assertJodaConvert;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.collect.TestHelper.coverPrivateConstructor;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.TenorAdjustment;

/**
 * Test Ibor Index.
 */
@Test
public class IborIndexTest {

  public void test_null() {
    IborIndex test = IborIndex.of("GBP-LIBOR-3M");
    assertThrowsIllegalArg(() -> test.calculateEffectiveFromFixing(null));
    assertThrowsIllegalArg(() -> test.calculateFixingFromEffective(null));
    assertThrowsIllegalArg(() -> test.calculateMaturityFromEffective(null));
  }

  public void test_gbpLibor3m() {
    IborIndex test = IborIndex.of("GBP-LIBOR-3M");
    assertEquals(test.getCurrency(), GBP);
    assertEquals(test.getName(), "GBP-LIBOR-3M");
    assertEquals(test.getTenor(), TENOR_3M);
    assertEquals(test.getFixingCalendar(), GBLO);
    assertEquals(test.getFixingDateOffset(),
        DaysAdjustment.ofCalendarDays(0, BusinessDayAdjustment.of(PRECEDING, GBLO)));
    assertEquals(test.getEffectiveDateOffset(),
        DaysAdjustment.ofCalendarDays(0, BusinessDayAdjustment.of(FOLLOWING, GBLO)));
    assertEquals(test.getMaturityDateOffset(),
        TenorAdjustment.ofLastBusinessDay(TENOR_3M, BusinessDayAdjustment.of(MODIFIED_FOLLOWING, GBLO)));
    assertEquals(test.getDayCount(), ACT_365F);
    assertEquals(test.toString(), "GBP-LIBOR-3M");
  }

  public void test_gbpLibor3m_dates() {
    IborIndex test = IborIndex.of("GBP-LIBOR-3M");
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 13)), date(2014, 10, 13));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 13)), date(2014, 10, 13));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 13)), date(2015, 1, 13));
    // weekend
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 10)), date(2014, 10, 10));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 10)), date(2014, 10, 10));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 10)), date(2015, 1, 12));
    // input date is Sunday
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 12)), date(2014, 10, 13));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 12)), date(2014, 10, 13));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 12)), date(2015, 1, 13));
  }

  public void test_usdLibor3m() {
    IborIndex test = IborIndex.of("USD-LIBOR-3M");
    assertEquals(test.getCurrency(), USD);
    assertEquals(test.getName(), "USD-LIBOR-3M");
    assertEquals(test.getTenor(), TENOR_3M);
    assertEquals(test.getFixingCalendar(), GBLO);
    assertEquals(test.getFixingDateOffset(), DaysAdjustment.ofBusinessDays(-2, GBLO));
    assertEquals(test.getEffectiveDateOffset(),
        DaysAdjustment.ofBusinessDays(2, GBLO, BusinessDayAdjustment.of(FOLLOWING, GBLO.combineWith(USNY))));
    assertEquals(test.getMaturityDateOffset(),
        TenorAdjustment.ofLastBusinessDay(TENOR_3M, BusinessDayAdjustment.of(MODIFIED_FOLLOWING, GBLO.combineWith(USNY))));
    assertEquals(test.getDayCount(), ACT_360);
    assertEquals(test.toString(), "USD-LIBOR-3M");
  }

  public void test_usdLibor3m_dates() {
    IborIndex test = IborIndex.of("USD-LIBOR-3M");
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 27)), date(2014, 10, 29));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 29)), date(2014, 10, 27));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 29)), date(2015, 1, 29));
    // weekend
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 10)), date(2014, 10, 14));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 14)), date(2014, 10, 10));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 14)), date(2015, 1, 14));
    // effective date is US holiday
    assertEquals(test.calculateEffectiveFromFixing(date(2015, 1, 16)), date(2015, 1, 20));
    assertEquals(test.calculateFixingFromEffective(date(2015, 1, 20)), date(2015, 1, 16));
    assertEquals(test.calculateMaturityFromEffective(date(2015, 1, 20)), date(2015, 4, 20));
    // input date is Sunday, 13th is US holiday, but not UK holiday (can fix, but not be effective)
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 12)), date(2014, 10, 15));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 12)), date(2014, 10, 10));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 12)), date(2015, 1, 14));
  }

  public void test_euibor3m() {
    IborIndex test = IborIndex.of("EUR-EURIBOR-3M");
    assertEquals(test.getCurrency(), EUR);
    assertEquals(test.getName(), "EUR-EURIBOR-3M");
    assertEquals(test.getTenor(), TENOR_3M);
    assertEquals(test.getFixingCalendar(), EUTA);
    assertEquals(test.getFixingDateOffset(), DaysAdjustment.ofBusinessDays(-2, EUTA));
    assertEquals(test.getEffectiveDateOffset(), DaysAdjustment.ofBusinessDays(2, EUTA));
    assertEquals(test.getMaturityDateOffset(),
        TenorAdjustment.ofLastBusinessDay(TENOR_3M, BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA)));
    assertEquals(test.getDayCount(), ACT_360);
    assertEquals(test.toString(), "EUR-EURIBOR-3M");
  }

  public void test_euribor3m_dates() {
    IborIndex test = IborIndex.of("EUR-EURIBOR-3M");
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 27)), date(2014, 10, 29));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 29)), date(2014, 10, 27));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 29)), date(2015, 1, 29));
    // weekend
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 10)), date(2014, 10, 14));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 14)), date(2014, 10, 10));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 14)), date(2015, 1, 14));
    // input date is Sunday
    assertEquals(test.calculateEffectiveFromFixing(date(2014, 10, 12)), date(2014, 10, 15));
    assertEquals(test.calculateFixingFromEffective(date(2014, 10, 12)), date(2014, 10, 9));
    assertEquals(test.calculateMaturityFromEffective(date(2014, 10, 12)), date(2015, 1, 13));
  }

  public void test_usdLibor_all() {
    assertEquals(IborIndex.of("USD-LIBOR-1W").getName(), "USD-LIBOR-1W");
    assertEquals(IborIndex.of("USD-LIBOR-1W"), IborIndices.USD_LIBOR_1W);
    assertEquals(IborIndex.of("USD-LIBOR-1M").getName(), "USD-LIBOR-1M");
    assertEquals(IborIndex.of("USD-LIBOR-1M"), IborIndices.USD_LIBOR_1M);
    assertEquals(IborIndex.of("USD-LIBOR-2M").getName(), "USD-LIBOR-2M");
    assertEquals(IborIndex.of("USD-LIBOR-2M"), IborIndices.USD_LIBOR_2M);
    assertEquals(IborIndex.of("USD-LIBOR-3M").getName(), "USD-LIBOR-3M");
    assertEquals(IborIndex.of("USD-LIBOR-3M"), IborIndices.USD_LIBOR_3M);
    assertEquals(IborIndex.of("USD-LIBOR-6M").getName(), "USD-LIBOR-6M");
    assertEquals(IborIndex.of("USD-LIBOR-6M"), IborIndices.USD_LIBOR_6M);
    assertEquals(IborIndex.of("USD-LIBOR-12M").getName(), "USD-LIBOR-12M");
    assertEquals(IborIndex.of("USD-LIBOR-12M"), IborIndices.USD_LIBOR_12M);
  }

  //-------------------------------------------------------------------------
  @DataProvider(name = "name")
  static Object[][] data_name() {
    return new Object[][] {
        {IborIndices.GBP_LIBOR_6M, "GBP-LIBOR-6M"},
        {IborIndices.CHF_LIBOR_6M, "CHF-LIBOR-6M"},
        {IborIndices.EUR_LIBOR_6M, "EUR-LIBOR-6M"},
        {IborIndices.JPY_LIBOR_6M, "JPY-LIBOR-6M"},
        {IborIndices.USD_LIBOR_6M, "USD-LIBOR-6M"},
        {IborIndices.EUR_EURIBOR_1M, "EUR-EURIBOR-1M"},
        {IborIndices.JPY_TIBOR_JAPAN_2M, "JPY-TIBOR-JAPAN-2M"},
        {IborIndices.JPY_TIBOR_EUROYEN_6M, "JPY-TIBOR-EUROYEN-6M"},
    };
  }

  @Test(dataProvider = "name")
  public void test_name(IborIndex convention, String name) {
    assertEquals(convention.getName(), name);
  }

  @Test(dataProvider = "name")
  public void test_toString(IborIndex convention, String name) {
    assertEquals(convention.toString(), name);
  }

  @Test(dataProvider = "name")
  public void test_of_lookup(IborIndex convention, String name) {
    assertEquals(IborIndex.of(name), convention);
  }

  @Test(dataProvider = "name")
  public void test_extendedEnum(IborIndex convention, String name) {
    ImmutableMap<String, IborIndex> map = IborIndex.extendedEnum().lookupAll();
    assertEquals(map.get(name), convention);
  }

  public void test_of_lookup_notFound() {
    assertThrowsIllegalArg(() -> IborIndex.of("Rubbish"));
  }

  public void test_of_lookup_null() {
    assertThrowsIllegalArg(() -> IborIndex.of(null));
  }

  //-------------------------------------------------------------------------
  public void test_equals() {
    ImmutableIborIndex a = ImmutableIborIndex.builder()
        .name("OGIBOR")
        .currency(Currency.GBP)
        .fixingCalendar(GBLO)
        .fixingDateOffset(DaysAdjustment.ofBusinessDays(-2, GBLO))
        .effectiveDateOffset(DaysAdjustment.ofBusinessDays(2, GBLO))
        .maturityDateOffset(TenorAdjustment.ofLastBusinessDay(TENOR_3M, BusinessDayAdjustment.NONE))
        .dayCount(ACT_360)
        .build();
    IborIndex b = a.toBuilder().name("Rubbish").build();
    assertEquals(a.equals(b), false);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    ImmutableIborIndex index = ImmutableIborIndex.builder()
        .name("OGIBOR")
        .currency(Currency.GBP)
        .fixingCalendar(GBLO)
        .fixingDateOffset(DaysAdjustment.ofBusinessDays(-2, GBLO))
        .effectiveDateOffset(DaysAdjustment.ofBusinessDays(2, GBLO))
        .maturityDateOffset(TenorAdjustment.ofLastBusinessDay(TENOR_3M, BusinessDayAdjustment.NONE))
        .dayCount(ACT_360)
        .build();
    coverImmutableBean(index);
    coverPrivateConstructor(IborIndices.class);
  }

  public void test_jodaConvert() {
    assertJodaConvert(IborIndex.class, IborIndices.GBP_LIBOR_12M);
  }

  public void test_serialization() {
    IborIndex index = ImmutableIborIndex.builder()
        .name("OGIBOR")
        .currency(Currency.GBP)
        .fixingCalendar(GBLO)
        .fixingDateOffset(DaysAdjustment.ofBusinessDays(-2, GBLO))
        .effectiveDateOffset(DaysAdjustment.ofBusinessDays(2, GBLO))
        .maturityDateOffset(TenorAdjustment.ofLastBusinessDay(TENOR_3M, BusinessDayAdjustment.NONE))
        .dayCount(ACT_360)
        .build();
    assertSerialization(index);
  }

}
