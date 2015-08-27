/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p/>
 * Please see distribution for license.
 */
package com.opengamma.strata.function.fpml;

import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.finance.rate.fra.Fra;
import com.opengamma.strata.finance.rate.fra.FraTrade;

@Test
public class FpmlTradeParserTest {

  public void loadGbpFra() {
    String file = "";
    ByteSource resource = Resources.asByteSource(Resources.getResource(file));
    List<Trade> trades = FpmlTradeParser.parseTrades(resource);
    assertEquals(trades.size(), 1);
    Trade trade = trades.get(0);
    assertEquals(trade.getClass(), FraTrade.class);
    FraTrade fraTrade = (FraTrade) trade;
    assertEquals(fraTrade.getTradeInfo().getTradeDate(), LocalDate.of(2015, 7, 7));
    Fra fra = fraTrade.getProduct();
    assertEquals(fra.getStartDate(), LocalDate.of(2015, 10, 7));
    assertEquals(fra.getEndDate(), LocalDate.of(2016, 4, 7));
    assertEquals(fra.getStartDate(), LocalDate.of(2015, 10, 7));
  }

//  private HolidayCalendar calendar(String name) {
//    return HolidayCalendar.of(name);
//  }
//
//  private HolidayCalendar calendar(String name1, String name2) {
//    return HolidayCalendar.of(name1).combineWith(HolidayCalendar.of(name2));
//  }

}
