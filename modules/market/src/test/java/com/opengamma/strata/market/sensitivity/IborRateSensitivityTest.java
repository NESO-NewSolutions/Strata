/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.market.sensitivity;

import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.index.IborIndices.GBP_LIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxMatrix;

/**
 * Test {@link IborRateSensitivity}.
 */
@Test
public class IborRateSensitivityTest {

  public void test_of_noCurrency() {
    IborRateSensitivity test = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    assertEquals(test.getIndex(), GBP_LIBOR_3M);
    assertEquals(test.getCurrency(), GBP);
    assertEquals(test.getFixingDate(), date(2015, 8, 27));
    assertEquals(test.getSensitivity(), 32d);
    assertEquals(test.getIndex(), GBP_LIBOR_3M);
  }

  public void test_of_withCurrency() {
    IborRateSensitivity test = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), GBP, 32d);
    assertEquals(test.getIndex(), GBP_LIBOR_3M);
    assertEquals(test.getCurrency(), GBP);
    assertEquals(test.getFixingDate(), date(2015, 8, 27));
    assertEquals(test.getSensitivity(), 32d);
    assertEquals(test.getIndex(), GBP_LIBOR_3M);
  }

  //-------------------------------------------------------------------------
  public void test_withCurrency() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    assertSame(base.withCurrency(GBP), base);

    IborRateSensitivity expected = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), USD, 32d);
    IborRateSensitivity test = base.withCurrency(USD);
    assertEquals(test, expected);
  }

  //-------------------------------------------------------------------------
  public void test_withSensitivity() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity expected = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 20d);
    IborRateSensitivity test = base.withSensitivity(20d);
    assertEquals(test, expected);
  }

  //-------------------------------------------------------------------------
  public void test_compareKey() {
    IborRateSensitivity a1 = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity a2 = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity b = IborRateSensitivity.of(USD_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity c = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), USD, 32d);
    IborRateSensitivity d = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 9, 27), 32d);
    ZeroRateSensitivity other = ZeroRateSensitivity.of(GBP, date(2015, 9, 27), 32d);
    assertEquals(a1.compareKey(a2), 0);
    assertEquals(a1.compareKey(b) < 0, true);
    assertEquals(b.compareKey(a1) > 0, true);
    assertEquals(a1.compareKey(c) < 0, true);
    assertEquals(c.compareKey(a1) > 0, true);
    assertEquals(a1.compareKey(d) < 0, true);
    assertEquals(d.compareKey(a1) > 0, true);
    assertEquals(a1.compareKey(other) < 0, true);
    assertEquals(other.compareKey(a1) > 0, true);
  }

  //-------------------------------------------------------------------------
  public void test_convertedTo() {
    LocalDate fixingDate = date(2015, 8, 27);
    double sensi = 32d;
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, fixingDate, sensi);
    double rate = 1.5d;
    FxMatrix matrix = FxMatrix.of(CurrencyPair.of(GBP, USD), rate);
    IborRateSensitivity test1 = (IborRateSensitivity) base.convertedTo(USD, matrix);
    IborRateSensitivity expected = IborRateSensitivity.of(GBP_LIBOR_3M, fixingDate, USD, sensi * rate);
    assertEquals(test1, expected);
    IborRateSensitivity test2 = (IborRateSensitivity) base.convertedTo(GBP, matrix);
    assertEquals(test2, base);
  }

  //-------------------------------------------------------------------------
  public void test_multipliedBy() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity expected = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d * 3.5d);
    IborRateSensitivity test = base.multipliedBy(3.5d);
    assertEquals(test, expected);
  }

  //-------------------------------------------------------------------------
  public void test_mapSensitivity() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity expected = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 1 / 32d);
    IborRateSensitivity test = base.mapSensitivity(s -> 1 / s);
    assertEquals(test, expected);
  }

  //-------------------------------------------------------------------------
  public void test_normalize() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity test = base.normalize();
    assertSame(test, base);
  }

  //-------------------------------------------------------------------------
  public void test_combinedWith() {
    IborRateSensitivity base1 = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity base2 = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 9, 27), 22d);
    MutablePointSensitivities expected = new MutablePointSensitivities();
    expected.add(base1).add(base2);
    PointSensitivityBuilder test = base1.combinedWith(base2);
    assertEquals(test, expected);
  }

  public void test_combinedWith_mutable() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    MutablePointSensitivities expected = new MutablePointSensitivities();
    expected.add(base);
    PointSensitivityBuilder test = base.combinedWith(new MutablePointSensitivities());
    assertEquals(test, expected);
  }

  //-------------------------------------------------------------------------
  public void test_buildInto() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    MutablePointSensitivities combo = new MutablePointSensitivities();
    MutablePointSensitivities test = base.buildInto(combo);
    assertSame(test, combo);
    assertEquals(test.getSensitivities(), ImmutableList.of(base));
  }

  //-------------------------------------------------------------------------
  public void test_build() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    PointSensitivities test = base.build();
    assertEquals(test.getSensitivities(), ImmutableList.of(base));
  }

  //-------------------------------------------------------------------------
  public void test_cloned() {
    IborRateSensitivity base = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    IborRateSensitivity test = base.cloned();
    assertSame(test, base);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    IborRateSensitivity test = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    coverImmutableBean(test);
    IborRateSensitivity test2 = IborRateSensitivity.of(USD_LIBOR_3M, date(2015, 7, 27), USD, 16d);
    coverBeanEquals(test, test2);
  }

  public void test_serialization() {
    IborRateSensitivity test = IborRateSensitivity.of(GBP_LIBOR_3M, date(2015, 8, 27), 32d);
    assertSerialization(test);
  }

}
