/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.function.fpml;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.io.File;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

import org.joda.beans.Bean;
import org.joda.beans.ser.JodaBeanSer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteSource;
import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.PayReceive;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConvention;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendars;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueAdjustment;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.basics.value.ValueStep;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.io.XmlElement;
import com.opengamma.strata.collect.io.XmlFile;
import com.opengamma.strata.finance.Product;
import com.opengamma.strata.finance.ProductTrade;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.finance.TradeInfo;
import com.opengamma.strata.finance.TradeInfo.Builder;
import com.opengamma.strata.finance.rate.fra.Fra;
import com.opengamma.strata.finance.rate.fra.FraDiscountingMethod;
import com.opengamma.strata.finance.rate.fra.FraTrade;
import com.opengamma.strata.finance.rate.swap.CompoundingMethod;
import com.opengamma.strata.finance.rate.swap.FixedRateCalculation;
import com.opengamma.strata.finance.rate.swap.FixingRelativeTo;
import com.opengamma.strata.finance.rate.swap.IborRateAveragingMethod;
import com.opengamma.strata.finance.rate.swap.IborRateCalculation;
import com.opengamma.strata.finance.rate.swap.KnownAmountSwapLeg;
import com.opengamma.strata.finance.rate.swap.NegativeRateMethod;
import com.opengamma.strata.finance.rate.swap.NotionalSchedule;
import com.opengamma.strata.finance.rate.swap.OvernightRateCalculation;
import com.opengamma.strata.finance.rate.swap.PaymentRelativeTo;
import com.opengamma.strata.finance.rate.swap.PaymentSchedule;
import com.opengamma.strata.finance.rate.swap.RateCalculation;
import com.opengamma.strata.finance.rate.swap.RateCalculationSwapLeg;
import com.opengamma.strata.finance.rate.swap.ResetSchedule;
import com.opengamma.strata.finance.rate.swap.StubCalculation;
import com.opengamma.strata.finance.rate.swap.Swap;
import com.opengamma.strata.finance.rate.swap.SwapLeg;
import com.opengamma.strata.finance.rate.swap.SwapTrade;

/**
 * Loader of trade data in FpML v5.8 format.
 * <p>
 * This handles the subset of FpML necessary to populate the trade model.
 */
public final class FpmlTradeParser {
  // Notes: Streaming trades directly from the file is difficult due to the
  // need to parse the party element at the root, which is after the trades

  /**
   * The 'href' attribute key.
   */
  private static final String HREF = "href";

  /**
   * The parsed file.
   */
  private final XmlElement fpmlRoot;
  /**
   * The map of references.
   */
  private final ImmutableMap<String, XmlElement> references;
  /**
   * Map of reference id to partyId.
   */
  private final ListMultimap<String, String> parties;
  /**
   * The party reference id.
   */
  private String ourPartyHrefId;

  //-------------------------------------------------------------------------
  /**
   * Parses the first trade from the specified source using the first party as ours.
   * 
   * @param source  the source of the FpML XML document
   * @return the parsed trades
   * @throws FpmlParseException if a parse error occurred
   */
  public static List<Trade> parseTrades(ByteSource source) {
    XmlFile xmlFile = XmlFile.of(source, "id");
    FpmlTradeParser parser = new FpmlTradeParser(xmlFile.getRoot(), xmlFile.getReferences(), null);
    return parser.parseTrades();
  }

  /**
   * Creates an instance, parsing the specified source.
   * 
   * @param source  the source of the FpML XML document
   * @param ourParty  our party identifier, as stored in {@code <partyId>}
   * @return the parsed trades
   * @throws FpmlParseException if a parse error occurred
   */
  public static List<Trade> parseTrades(ByteSource source, String ourParty) {
    XmlFile xmlFile = XmlFile.of(source, "id");
    FpmlTradeParser parser = new FpmlTradeParser(xmlFile.getRoot(), xmlFile.getReferences(), ourParty);
    return parser.parseTrades();
  }

  /**
   * Creates an instance, based on the specified element.
   * <p>
   * The map of references is used to link one part of the XML to another.
   * For example, if one part of the XML has {@code <foo id="fooId">}, the references
   * map will contain an entry mapping "fooId" to the parsed element {@code <foo>}.
   * 
   * @param fpmlRootEl  the source of the FpML XML document
   * @param references  the map of id/href to referenced element
   * @param ourParty  our party identifier, as stored in {@code <partyId>}
   */
  private FpmlTradeParser(XmlElement fpmlRootEl, Map<String, XmlElement> references, String ourParty) {
    this.fpmlRoot = fpmlRootEl;
    this.references = ImmutableMap.copyOf(references);
    this.parties = parseParties(fpmlRootEl);
    this.ourPartyHrefId = findOurParty(ourParty);
  }

  // parse all the root-level party elements
  private static ListMultimap<String, String> parseParties(XmlElement root) {
    ListMultimap<String, String> parties = ArrayListMultimap.create();
    for (XmlElement child : root.getChildren("party")) {
      parties.putAll(child.getAttribute("id"), findPartyIds(child));
    }
    return ImmutableListMultimap.copyOf(parties);
  }

  // find the party identifiers
  private static List<String> findPartyIds(XmlElement party) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (XmlElement child : party.getChildren("partyId")) {
      if (child.hasContent()) {
        builder.add(child.getContent());
      }
    }
    return builder.build();
  }

  //-------------------------------------------------------------------------
  /**
   * Loads the FpML input source into a trade.
   * 
   * @return the default pricing rules
   * @throws FpmlParseException if a parse error occurred
   */
  private List<Trade> parseTrades() {
    try {
      List<XmlElement> tradeEls = fpmlRoot.getChildren("trade");
      ImmutableList.Builder<Trade> builder = ImmutableList.builder();
      for (XmlElement tradeEl : tradeEls) {
        builder.add(parseTrade(tradeEl));
      }
      return builder.build();

    } catch (FpmlParseException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new FpmlParseException(ex);
    }
  }

  private Trade parseTrade(XmlElement tradeEl) {
    // element 'otherPartyPayment' is ignored
    // tradeHeader
    TradeInfo.Builder tradeInfoBuilder = TradeInfo.builder();
    XmlElement tradeHeaderEl = tradeEl.getChild("tradeHeader");
    tradeInfoBuilder.tradeDate(parseDate(tradeHeaderEl.getChild("tradeDate")));
    List<XmlElement> partyTradeIdentifierEls = tradeHeaderEl.getChildren("partyTradeIdentifier");
    for (XmlElement partyTradeIdentifierEl : partyTradeIdentifierEls) {
      String partyReferenceHref = partyTradeIdentifierEl.getChild("partyReference").getAttribute(HREF);
      if (partyReferenceHref.equals(ourPartyHrefId)) {
        XmlElement firstTradeIdEl = partyTradeIdentifierEl.getChildren("tradeId").get(0);
        String tradeIdValue = firstTradeIdEl.getContent();
        tradeInfoBuilder.id(StandardId.of("FpML-tradeId", tradeIdValue));  // TODO: tradeIdScheme not used as URI clashes
      }
    }
    Optional<XmlElement> fra = tradeEl.getChildOptional("fra");
    if (fra.isPresent()) {
      return parseFraTrade(fra.get(), tradeInfoBuilder);
    }
    Optional<XmlElement> swap = tradeEl.getChildOptional("swap");
    if (swap.isPresent()) {
      return parseSwapTrade(swap.get(), tradeInfoBuilder);
    }
    throw new FpmlParseException("Unknown product type, not fra or swap");
  }

  //-------------------------------------------------------------------------
  // FRA
  //-------------------------------------------------------------------------
  private FraTrade parseFraTrade(XmlElement fraEl, Builder tradeInfoBuilder) {
    // supported elements:
    //  'buyerPartyReference'
    //  'sellerPartyReference'
    //  'adjustedTerminationDate'
    //  'paymentDate'
    //  'fixingDateOffset'
    //  'dayCountFraction'
    //  'notional'
    //  'fixedRate'
    //  'floatingRateIndex'
    //  'indexTenor+'
    //  'fraDiscounting'
    // ignored elements:
    //  'Product.model?'
    //  'buyerAccountReference?'
    //  'sellerAccountReference?'
    //  'calculationPeriodNumberOfDays'
    //  'additionalPayment*'
    Fra.Builder fraBuilder = Fra.builder();
    // buy/sell and counterparty
    String buyerPartyReference = fraEl.getChild("buyerPartyReference").getAttribute(HREF);
    String sellerPartyReference = fraEl.getChild("sellerPartyReference").getAttribute(HREF);
    if (ourPartyHrefId.isEmpty() || buyerPartyReference.equals(ourPartyHrefId)) {
      fraBuilder.buySell(BuySell.BUY);
      tradeInfoBuilder.counterparty(StandardId.of("FpML-partyId", parties.get(sellerPartyReference).get(0)));
    } else if (sellerPartyReference.equals(ourPartyHrefId)) {
      fraBuilder.buySell(BuySell.SELL);
      tradeInfoBuilder.counterparty(StandardId.of("FpML-partyId", parties.get(buyerPartyReference).get(0)));
    } else {
      throw new FpmlParseException(Messages.format(
          "Neither buyerPartyReference nor sellerPartyReference contain our party ID: {}", ourPartyHrefId));
    }
    // start date
    fraBuilder.startDate(parseDate(fraEl.getChild("adjustedEffectiveDate")));
    // end date
    fraBuilder.endDate(parseDate(fraEl.getChild("adjustedTerminationDate")));
    // payment date
    fraBuilder.paymentDate(parseAdjustableDate(fraEl.getChild("paymentDate")));
    // fixing offset
    fraBuilder.fixingDateOffset(parseRelativeDateOffsetDays(fraEl.getChild("fixingDateOffset")));
    // dateRelativeTo required to refer to adjustedEffectiveDate, so ignored here
    // day count
    fraBuilder.dayCount(parseDayCountFraction(fraEl.getChild("dayCountFraction")));
    // notional
    CurrencyAmount notional = parseCurrencyAmount(fraEl.getChild("notional"));
    fraBuilder.currency(notional.getCurrency());
    fraBuilder.notional(notional.getAmount());
    // fixed rate
    fraBuilder.fixedRate(parseDecimal(fraEl.getChild("fixedRate")));
    // index
    List<Index> indexes = parseIndexes(fraEl);
    switch (indexes.size()) {
      case 1:
        fraBuilder.index((IborIndex) indexes.get(0));
        break;
      case 2:
        fraBuilder.index((IborIndex) indexes.get(0));
        fraBuilder.indexInterpolated((IborIndex) indexes.get(1));
        break;
      default:
        throw new FpmlParseException("Expected one or two indexes, but found " + indexes.size());
    }
    // discounting
    fraBuilder.discounting(FraDiscountingMethod.of(fraEl.getChild("fraDiscounting").getContent()));

    return FraTrade.builder()
        .tradeInfo(tradeInfoBuilder.build())
        .product(fraBuilder.build())
        .build();
  }

  //-------------------------------------------------------------------------
  // Swap
  //-------------------------------------------------------------------------
  private SwapTrade parseSwapTrade(XmlElement swapEl, Builder tradeInfoBuilder) {
    // supported elements:
    //  'swapStream+'
    //  'swapStream/buyerPartyReference'
    //  'swapStream/sellerPartyReference'
    //  'swapStream/calculationPeriodDates'
    //  'swapStream/paymentDates'
    //  'swapStream/resetDates?'
    //  'swapStream/calculationPeriodAmount'
    //  'swapStream/stubCalculationPeriodAmount?'
    //  'swapStream/principalExchanges?'
    //  'swapStream/calculationPeriodAmount/knownAmountSchedule'
    // ignored elements:
    //  'Product.model?'
    //  'swapStream/cashflows?'
    //  'swapStream/settlementProvision?'
    //  'swapStream/formula?'
    //  'earlyTerminationProvision?'
    //  'cancelableProvision?'
    //  'extendibleProvision?'
    //  'additionalPayment*'
    //  'additionalTerms?'
    // rejected elements:
    //  'swapStream/calculationPeriodAmount/calculation/fxLinkedNotionalSchedule'
    //  'swapStream/calculationPeriodAmount/calculation/futureValueNotional'
    ImmutableList<XmlElement> legEls = swapEl.getChildren("swapStream");
    ImmutableList.Builder<SwapLeg> legsBuilder = ImmutableList.builder();
    for (XmlElement legEl : legEls) {
      // calculation
      XmlElement calcPeriodAmountEl = legEl.getChild("calculationPeriodAmount");
      XmlElement calcEl = calcPeriodAmountEl.getChildOptional("calculation")
          .orElse(XmlElement.ofChildren("calculation", ImmutableList.of()));
      PeriodicSchedule accrualSchedule = parseSwapAccrualSchedule(legEl);
      PaymentSchedule paymentSchedule = parseSwapPaymentSchedule(legEl, calcEl);
      // known amount or rate calculation
      Optional<XmlElement> knownAmountOptEl = calcPeriodAmountEl.getChildOptional("knownAmountSchedule");
      if (knownAmountOptEl.isPresent()) {
        XmlElement knownAmountEl = knownAmountOptEl.get();
        validateNotPresent(legEl, "stubCalculationPeriodAmount");
        validateNotPresent(legEl, "resetDates");
        // pay/receive and counterparty
        PayReceive payReceive = parsePayerReceiver(legEl, tradeInfoBuilder);
        ValueSchedule notionalSchedule = parseSchedule(knownAmountEl);
        // build
        legsBuilder.add(KnownAmountSwapLeg.builder()
            .payReceive(payReceive)
            .accrualSchedule(accrualSchedule)
            .paymentSchedule(paymentSchedule)
            .amount(notionalSchedule)
            .currency(parseCurrency(knownAmountEl.getChild("currency")))
            .build());
      } else {
        validateNotPresent(calcEl, "fxLinkedNotionalSchedule");
        validateNotPresent(calcEl, "futureValueNotional");
        // pay/receive and counterparty
        PayReceive payReceive = parsePayerReceiver(legEl, tradeInfoBuilder);
        NotionalSchedule notionalSchedule = parseSwapNotionalSchedule(legEl, calcEl);
        RateCalculation calculation = parseSwapCalculation(legEl, calcEl, accrualSchedule);
        // build
        legsBuilder.add(RateCalculationSwapLeg.builder()
            .payReceive(payReceive)
            .accrualSchedule(accrualSchedule)
            .paymentSchedule(paymentSchedule)
            .notionalSchedule(notionalSchedule)
            .calculation(calculation)
            .build());
      }
    }
    return SwapTrade.builder()
        .tradeInfo(tradeInfoBuilder.build())
        .product(Swap.of(legsBuilder.build()))
        .build();
  }

  // parses the accrual schedule
  private PeriodicSchedule parseSwapAccrualSchedule(XmlElement legEl) {
    // supported elements:
    //  'calculationPeriodDates/effectiveDate'
    //  'calculationPeriodDates/relativeEffectiveDate'
    //  'calculationPeriodDates/terminationDate'
    //  'calculationPeriodDates/relativeTerminationDate'
    //  'calculationPeriodDates/calculationPeriodDates'
    //  'calculationPeriodDates/calculationPeriodDatesAdjustments'
    //  'calculationPeriodDates/firstPeriodStartDate?'
    //  'calculationPeriodDates/firstRegularPeriodStartDate?'
    //  'calculationPeriodDates/lastRegularPeriodEndDate?'
    //  'calculationPeriodDates/stubPeriodType?'
    //  'calculationPeriodDates/calculationPeriodFrequency'
    // ignored elements:
    //  'calculationPeriodDates/firstCompoundingPeriodEndDate?'
    PeriodicSchedule.Builder accrualScheduleBuilder = PeriodicSchedule.builder();
    // calculation dates
    XmlElement calcPeriodDatesEl = legEl.getChild("calculationPeriodDates");
    // business day adjustments
    BusinessDayAdjustment bda = parseBusinessDayAdjustments(
        calcPeriodDatesEl.getChild("calculationPeriodDatesAdjustments"));
    accrualScheduleBuilder.businessDayAdjustment(bda);
    // start date
    AdjustableDate startDate = calcPeriodDatesEl.getChildOptional("effectiveDate")
        .map(el -> parseAdjustableDate(el))
        .orElseGet(() -> parseAdjustedRelativeDateOffset(calcPeriodDatesEl.getChild("relativeEffectiveDate")));
    accrualScheduleBuilder.startDate(startDate.getUnadjusted());
    if (!bda.equals(startDate.getAdjustment())) {
      accrualScheduleBuilder.startDateBusinessDayAdjustment(startDate.getAdjustment());
    }
    // end date
    AdjustableDate endDate = calcPeriodDatesEl.getChildOptional("terminationDate")
        .map(el -> parseAdjustableDate(el))
        .orElseGet(() -> parseAdjustedRelativeDateOffset(calcPeriodDatesEl.getChild("relativeTerminationDate")));
    accrualScheduleBuilder.endDate(endDate.getUnadjusted());
    if (!bda.equals(endDate.getAdjustment())) {
      accrualScheduleBuilder.endDateBusinessDayAdjustment(endDate.getAdjustment());
    }
    // first date (overwrites the start date)
    calcPeriodDatesEl.getChildOptional("firstPeriodStartDate").ifPresent(el -> {
      AdjustableDate actualStartDate = parseAdjustableDate(el);
      accrualScheduleBuilder.startDate(actualStartDate.getUnadjusted());
      if (!bda.equals(actualStartDate.getAdjustment())) {
        accrualScheduleBuilder.startDateBusinessDayAdjustment(actualStartDate.getAdjustment());
      }
    });
    // first regular date
    calcPeriodDatesEl.getChildOptional("firstRegularPeriodStartDate").ifPresent(el -> {
      accrualScheduleBuilder.firstRegularStartDate(parseDate(el));
    });
    // last regular date
    calcPeriodDatesEl.getChildOptional("lastRegularPeriodEndDate").ifPresent(el -> {
      accrualScheduleBuilder.lastRegularEndDate(parseDate(el));
    });
    // stub type
    calcPeriodDatesEl.getChildOptional("stubPeriodType").ifPresent(el -> {
      accrualScheduleBuilder.stubConvention(parseStubConvention(el));
    });
    // frequency
    XmlElement freqEl = calcPeriodDatesEl.getChild("calculationPeriodFrequency");
    Frequency accrualFreq = parseFrequency(freqEl);
    accrualScheduleBuilder.frequency(accrualFreq);
    // roll convention
    accrualScheduleBuilder.rollConvention(
        FpmlConversions.rollConvention(freqEl.getChild("rollConvention").getContent()));
    return accrualScheduleBuilder.build();
  }

  // parses the payment schedule
  private PaymentSchedule parseSwapPaymentSchedule(XmlElement legEl, XmlElement calcEl) {
    // supported elements:
    //  'paymentDates/paymentFrequency'
    //  'paymentDates/payRelativeTo'
    //  'paymentDates/paymentDaysOffset?'
    //  'paymentDates/paymentDatesAdjustments'
    //  'calculationPeriodAmount/calculation/compoundingMethod'
    // ignored elements:
    //  'paymentDates/calculationPeriodDatesReference'
    //  'paymentDates/resetDatesReference'
    //  'paymentDates/valuationDatesReference'
    //  'paymentDates/firstPaymentDate?'
    //  'paymentDates/lastRegularPaymentDate?'
    PaymentSchedule.Builder paymentScheduleBuilder = PaymentSchedule.builder();
    // payment dates
    XmlElement paymentDatesEl = legEl.getChild("paymentDates");
    // frequency
    paymentScheduleBuilder.paymentFrequency(parseFrequency(
        paymentDatesEl.getChild("paymentFrequency")));
    paymentScheduleBuilder.paymentRelativeTo(parsePayRelativeTo(paymentDatesEl.getChild("payRelativeTo")));
    // offset
    Optional<XmlElement> paymentOffsetEl = paymentDatesEl.getChildOptional("paymentDaysOffset");
    BusinessDayAdjustment payAdjustment = parseBusinessDayAdjustments(
        paymentDatesEl.getChild("paymentDatesAdjustments"));
    if (paymentOffsetEl.isPresent()) {
      Period period = parsePeriod(paymentOffsetEl.get());
      if (period.toTotalMonths() != 0) {
        throw new FpmlParseException("Invalid 'paymentDatesAdjustments' value, expected days-based period: " + period);
      }
      Optional<XmlElement> dayTypeEl = paymentOffsetEl.get().getChildOptional("dayType");
      boolean fixingCalendarDays = period.isZero() ||
          (dayTypeEl.isPresent() && dayTypeEl.get().getContent().equals("Calendar"));
      if (fixingCalendarDays) {
        paymentScheduleBuilder.paymentDateOffset(DaysAdjustment.ofCalendarDays(period.getDays(), payAdjustment));
      } else {
        paymentScheduleBuilder.paymentDateOffset(DaysAdjustment.ofBusinessDays(period.getDays(), payAdjustment.getCalendar()));
      }
    } else {
      paymentScheduleBuilder.paymentDateOffset(DaysAdjustment.ofCalendarDays(0, payAdjustment));
    }
    // compounding
    calcEl.getChildOptional("compoundingMethod").ifPresent(compoundingEl -> {
      paymentScheduleBuilder.compoundingMethod(CompoundingMethod.of(compoundingEl.getContent()));
    });
    return paymentScheduleBuilder.build();
  }

  // parses the notional schedule
  private NotionalSchedule parseSwapNotionalSchedule(XmlElement legEl, XmlElement calcEl) {
    // supported elements:
    //  'principalExchanges/initialExchange'
    //  'principalExchanges/finalExchange'
    //  'principalExchanges/intermediateExchange'
    //  'calculationPeriodAmount/calculation/notionalSchedule'
    // rejected elements:
    //  'calculationPeriodAmount/calculation/notionalSchedule/notionalStepParameters'
    NotionalSchedule.Builder notionalScheduleBuilder = NotionalSchedule.builder();
    // exchanges
    legEl.getChildOptional("principalExchanges").ifPresent(el -> {
      notionalScheduleBuilder.initialExchange(Boolean.parseBoolean(el.getChild("initialExchange").getContent()));
      notionalScheduleBuilder.intermediateExchange(
          Boolean.parseBoolean(el.getChild("intermediateExchange").getContent()));
      notionalScheduleBuilder.finalExchange(Boolean.parseBoolean(el.getChild("finalExchange").getContent()));
    });
    // notional schedule
    XmlElement notionalEl = calcEl.getChild("notionalSchedule");
    validateNotPresent(notionalEl, "notionalStepParameters");
    XmlElement notionalScheduleEl = notionalEl.getChild("notionalStepSchedule");
    notionalScheduleBuilder.amount(parseSchedule(notionalScheduleEl));
    notionalScheduleBuilder.currency(parseCurrency(notionalScheduleEl.getChild("currency")));
    return notionalScheduleBuilder.build();
  }

  // parse swap rate calculation
  private RateCalculation parseSwapCalculation(XmlElement legEl, XmlElement calcEl, PeriodicSchedule accrualSchedule) {
    // supported elements:
    //  'calculationPeriodAmount/calculation/fixedRateSchedule'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/floatingRateIndex'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/indexTenor?'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/floatingRateMultiplierSchedule?'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/spreadSchedule*'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/initialRate?' (Ibor only)
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/averagingMethod?'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/negativeInterestRateTreatment?'
    //  'calculationPeriodAmount/calculation/dayCountFraction'
    //  'resetDates/resetRelativeTo'
    //  'resetDates/fixingDates'
    //  'resetDates/rateCutOffDaysOffset' (OIS only)
    //  'resetDates/resetFrequency'
    //  'resetDates/resetDatesAdjustments'
    //  'stubCalculationPeriodAmount/initalStub' (Ibor only)
    //  'stubCalculationPeriodAmount/finalStub' (Ibor only)
    // ignored elements:
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/finalRateRounding?'
    //  'calculationPeriodAmount/calculation/discounting?'
    //  'resetDates/calculationPeriodDatesReference'
    // rejected elements:
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/spreadSchedule/type?'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/rateTreatment?'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/capRateSchedule?'
    //  'calculationPeriodAmount/calculation/floatingRateCalculation/floorRateSchedule?'
    //  'resetDates/initialFixingDate'
    //  'stubCalculationPeriodAmount/initalStub/stubAmount'
    //  'stubCalculationPeriodAmount/finalStub/stubAmount'
    Optional<XmlElement> fixedOptEl = calcEl.getChildOptional("fixedRateSchedule");
    Optional<XmlElement> floatingOptEl = calcEl.getChildOptional("floatingRateCalculation");

    if (fixedOptEl.isPresent()) {
      // fixed
      // TODO: stubCalculationPeriodAmount could affect this
      return FixedRateCalculation.builder()
          .rate(parseSchedule(fixedOptEl.get()))
          .dayCount(parseDayCountFraction(calcEl.getChild("dayCountFraction")))
          .build();

    } else if (floatingOptEl.isPresent()) {
      // float
      XmlElement floatingEl = floatingOptEl.get();
      validateNotPresent(floatingEl, "rateTreatment");
      validateNotPresent(floatingEl, "capRateSchedule");
      validateNotPresent(floatingEl, "floorRateSchedule");
      Index index = parseIndex(floatingEl);
      if (index instanceof IborIndex) {
        IborRateCalculation.Builder iborRateBuilder = IborRateCalculation.builder();
        // day count
        iborRateBuilder.dayCount(parseDayCountFraction(calcEl.getChild("dayCountFraction")));
        // index
        iborRateBuilder.index((IborIndex) parseIndex(floatingEl));
        // gearing
        floatingEl.getChildOptional("floatingRateMultiplierSchedule").ifPresent(el -> {
          iborRateBuilder.gearing(parseSchedule(el));
        });
        // spread
        if (floatingEl.getChildren("spreadSchedule").size() > 1) {
          throw new FpmlParseException("Only one 'spreadSchedule' is supported");
        }
        floatingEl.getChildOptional("spreadSchedule").ifPresent(el -> {
          validateNotPresent(el, "type");
          iborRateBuilder.spread(parseSchedule(el));
        });
        // initial fixed rate
        floatingEl.getChildOptional("initialRate").ifPresent(el -> {
          iborRateBuilder.firstRegularRate(parseDecimal(el));
        });
        // negative rates
        floatingEl.getChildOptional("negativeInterestRateTreatment").ifPresent(el -> {
          iborRateBuilder.negativeRateMethod(parseNegativeInterestRateTreatment(el));
        });
        // resets
        XmlElement resetDatesEl = legEl.getChild("resetDates");
        validateNotPresent(resetDatesEl, "initialFixingDate");
        validateNotPresent(resetDatesEl, "rateCutOffDaysOffset");
        resetDatesEl.getChildOptional("resetRelativeTo").ifPresent(el -> {
          iborRateBuilder.fixingRelativeTo(parseResetRelativeTo(el));
        });
        // fixing date offset
        iborRateBuilder.fixingDateOffset(parseRelativeDateOffsetDays(resetDatesEl.getChild("fixingDates")));
        Frequency resetFreq = parseFrequency(resetDatesEl.getChild("resetFrequency"));
        if (!accrualSchedule.getFrequency().equals(resetFreq)) {
          ResetSchedule.Builder resetScheduleBuilder = ResetSchedule.builder();
          resetScheduleBuilder.resetFrequency(resetFreq);
          floatingEl.getChildOptional("averagingMethod").ifPresent(el -> {
            resetScheduleBuilder.averagingMethod(parseAveragingMethod(el));
          });
          resetScheduleBuilder.businessDayAdjustment(
              parseBusinessDayAdjustments(resetDatesEl.getChild("resetDatesAdjustments")));
          iborRateBuilder.resetPeriods(resetScheduleBuilder.build());
        }
        // stubs
        legEl.getChildOptional("stubCalculationPeriodAmount").ifPresent(stubsEl -> {
          stubsEl.getChildOptional("initialStub").ifPresent(el -> {
            iborRateBuilder.initialStub(parseStubCalculation(el));
          });
          stubsEl.getChildOptional("finalStub").ifPresent(el -> {
            iborRateBuilder.finalStub(parseStubCalculation(el));
          });
        });
        return iborRateBuilder.build();

      } else if (index instanceof OvernightIndex) {
        OvernightRateCalculation.Builder overnightRateBuilder = OvernightRateCalculation.builder();
        validateNotPresent(legEl, "stubCalculationPeriodAmount");
        validateNotPresent(floatingEl, "initialRate");  // TODO: should support this in the model
        // day count
        overnightRateBuilder.dayCount(parseDayCountFraction(calcEl.getChild("dayCountFraction")));
        // index
        overnightRateBuilder.index((OvernightIndex) parseIndex(floatingEl));
        // accrual method
        FpmlFloatingRateIndex idx = FpmlFloatingRateIndex.of(floatingEl.getChild("floatingRateIndex").getContent());
        overnightRateBuilder.accrualMethod(idx.toOvernightAccrualMethod());
        // gearing
        floatingEl.getChildOptional("floatingRateMultiplierSchedule").ifPresent(el -> {
          overnightRateBuilder.gearing(parseSchedule(el));
        });
        // spread
        if (floatingEl.getChildren("spreadSchedule").size() > 1) {
          throw new FpmlParseException("Only one 'spreadSchedule' is supported");
        }
        floatingEl.getChildOptional("spreadSchedule").ifPresent(el -> {
          validateNotPresent(el, "type");
          overnightRateBuilder.spread(parseSchedule(el));
        });
        // negative rates
        floatingEl.getChildOptional("negativeInterestRateTreatment").ifPresent(el -> {
          overnightRateBuilder.negativeRateMethod(parseNegativeInterestRateTreatment(el));
        });
        // rate cut off
        XmlElement resetDatesEl = legEl.getChild("resetDates");
        validateNotPresent(resetDatesEl, "initialFixingDate");
        resetDatesEl.getChildOptional("rateCutOffDaysOffset").ifPresent(el -> {
          Period cutOff = parsePeriod(el);
          if (cutOff.toTotalMonths() != 0) {
            throw new FpmlParseException("Invalid 'rateCutOffDaysOffset' value, expected days-based period: " + cutOff);
          }
          overnightRateBuilder.rateCutOffDays(-cutOff.getDays());
        });
        return overnightRateBuilder.build();

      } else {
        throw new FpmlParseException("Invalid 'floatingRateIndex' type, not Ibor or Overnight");
      }

    } else {
      throw new FpmlParseException("Invalid 'calculation' type, not fixedRateSchedule or floatingRateCalculation");
    }
  }

  //-------------------------------------------------------------------------
  // Converts an FpML 'StubValue' to a {@code StubCalculation}.
  private StubCalculation parseStubCalculation(XmlElement baseEl) {
    validateNotPresent(baseEl, "stubAmount");
    Optional<XmlElement> rateOptEl = baseEl.getChildOptional("stubRate");
    if (rateOptEl.isPresent()) {
      return StubCalculation.ofFixedRate(parseDecimal(rateOptEl.get()));
    }
    List<XmlElement> indicesEls = baseEl.getChildren("floatingRate");
    if (indicesEls.size() == 1) {
      XmlElement indexEl = indicesEls.get(0);
      validateNotPresent(indexEl, "floatingRateMultiplierSchedule");
      validateNotPresent(indexEl, "spreadSchedule");
      validateNotPresent(indexEl, "rateTreatment");
      validateNotPresent(indexEl, "capRateSchedule");
      validateNotPresent(indexEl, "floorRateSchedule");
      return StubCalculation.ofIborRate((IborIndex) parseIndex(indexEl));
    } else if (indicesEls.size() == 2) {
      XmlElement index1El = indicesEls.get(0);
      validateNotPresent(index1El, "floatingRateMultiplierSchedule");
      validateNotPresent(index1El, "spreadSchedule");
      validateNotPresent(index1El, "rateTreatment");
      validateNotPresent(index1El, "capRateSchedule");
      validateNotPresent(index1El, "floorRateSchedule");
      XmlElement index2El = indicesEls.get(1);
      validateNotPresent(index2El, "floatingRateMultiplierSchedule");
      validateNotPresent(index2El, "spreadSchedule");
      validateNotPresent(index2El, "rateTreatment");
      validateNotPresent(index2El, "capRateSchedule");
      validateNotPresent(index2El, "floorRateSchedule");
      return StubCalculation.ofIborInterpolatedRate((IborIndex) parseIndex(index1El), (IborIndex) parseIndex(index2El));
    }
    throw new FpmlParseException("Unknown stub structure: " + baseEl);
  }

  // Converts an FpML 'StubPeriodTypeEnum' to a {@code StubConvention}.
  private StubConvention parseStubConvention(XmlElement baseEl) {
    if (baseEl.getContent().equals("ShortInitial")) {
      return StubConvention.SHORT_INITIAL;
    } else if (baseEl.getContent().equals("ShortFinal")) {
      return StubConvention.SHORT_FINAL;
    } else if (baseEl.getContent().equals("LongInitial")) {
      return StubConvention.LONG_INITIAL;
    } else if (baseEl.getContent().equals("LongFinal")) {
      return StubConvention.LONG_FINAL;
    } else {
      throw new FpmlParseException(Messages.format("Unknown 'stubPeriodType': {}", baseEl.getContent()));
    }
  }

  // Converts an FpML 'PayRelativeToEnum' to a {@code PaymentRelativeTo}.
  private PaymentRelativeTo parsePayRelativeTo(XmlElement baseEl) {
    if (baseEl.getContent().equals("CalculationPeriodStartDate")) {
      return PaymentRelativeTo.PERIOD_START;
    } else if (baseEl.getContent().equals("CalculationPeriodEndDate")) {
      return PaymentRelativeTo.PERIOD_END;
    } else {
      throw new FpmlParseException(Messages.format("Unknown 'payRelativeTo': {}", baseEl.getContent()));
    }
  }

  // Converts and FpML 'NegativeInterestRateTreatmentEnum' to a {@code NegativeRateMethod}.
  private NegativeRateMethod parseNegativeInterestRateTreatment(XmlElement baseEl) {
    if (baseEl.getContent().equals("NegativeInterestRateMethod")) {
      return NegativeRateMethod.ALLOW_NEGATIVE;
    } else if (baseEl.getContent().equals("ZeroInterestRateMethod")) {
      return NegativeRateMethod.NOT_NEGATIVE;
    } else {
      throw new FpmlParseException(Messages.format("Unknown 'negativeInterestRateTreatment': {}", baseEl.getContent()));
    }
  }

  // Converts an FpML 'AveragingMethodEnum' to a {@code IborRateAveragingMethod}.
  private IborRateAveragingMethod parseAveragingMethod(XmlElement baseEl) {
    if (baseEl.getContent().equals("Unweighted")) {
      return IborRateAveragingMethod.UNWEIGHTED;
    } else if (baseEl.getContent().equals("Weighted")) {
      return IborRateAveragingMethod.WEIGHTED;
    } else {
      throw new FpmlParseException(Messages.format("Unknown 'averagingMethod': {}", baseEl.getContent()));
    }
  }

  // Converts an FpML 'ResetRelativeToEnum' to a {@code FixingRelativeTo}.
  private FixingRelativeTo parseResetRelativeTo(XmlElement baseEl) {
    if (baseEl.getContent().equals("CalculationPeriodStartDate")) {
      return FixingRelativeTo.PERIOD_START;
    } else if (baseEl.getContent().equals("CalculationPeriodEndDate")) {
      return FixingRelativeTo.PERIOD_END;
    } else {
      throw new FpmlParseException(Messages.format("Unknown 'resetRelativeTo': {}", baseEl.getContent()));
    }
  }

  //-------------------------------------------------------------------------
  // helper methods for FpML types
  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'PayerReceiver.model' to a {@code PayReceive}.
   * 
   * @param baseEl  the FpML payer receiver model element
   * @return the pay/receive flag
   */
  private PayReceive parsePayerReceiver(XmlElement baseEl, TradeInfo.Builder tradeInfoBuilder) {
    String payerPartyReference = baseEl.getChild("payerPartyReference").getAttribute(HREF);
    String receiverPartyReference = baseEl.getChild("receiverPartyReference").getAttribute(HREF);
    Object currentCounterparty = tradeInfoBuilder.get(TradeInfo.meta().counterparty());
    // determine direction and setup counterparty
    if (ourPartyHrefId.isEmpty() || payerPartyReference.equals(ourPartyHrefId)) {
      StandardId proposedCounterparty = StandardId.of("FpML-partyId", parties.get(receiverPartyReference).get(0));
      if (currentCounterparty == null) {
        tradeInfoBuilder.counterparty(proposedCounterparty);
      } else if (!currentCounterparty.equals(proposedCounterparty)) {
        throw new FpmlParseException(Messages.format(
            "Two different counterparties found: {} and {}", currentCounterparty, proposedCounterparty));
      }
      return PayReceive.PAY;

    } else if (receiverPartyReference.equals(ourPartyHrefId)) {
      StandardId proposedCounterparty = StandardId.of("FpML-partyId", parties.get(payerPartyReference).get(0));
      if (currentCounterparty == null) {
        tradeInfoBuilder.counterparty(proposedCounterparty);
      } else if (!currentCounterparty.equals(proposedCounterparty)) {
        throw new FpmlParseException(Messages.format(
            "Two different counterparties found: {} and {}", currentCounterparty, proposedCounterparty));
      }
      return PayReceive.RECEIVE;

    } else {
      throw new FpmlParseException(Messages.format(
          "Neither payerPartyReference nor receiverPartyReference contain our party ID: {}", ourPartyHrefId));
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'Schedule' to a {@code ValueSchedule}.
   * 
   * @param baseEl  the FpML schedule element
   * @return the schedule
   */
  private ValueSchedule parseSchedule(XmlElement notionalScheduleEl) {
    // FpML content: ('initialValue', 'step*')
    // FpML 'step' content: ('stepDate', 'stepValue')
    double initialValue = parseDecimal(notionalScheduleEl.getChild("initialValue"));
    List<XmlElement> stepEls = notionalScheduleEl.getChildren("step");
    ImmutableList.Builder<ValueStep> stepBuilder = ImmutableList.builder();
    for (XmlElement stepEl : stepEls) {
      LocalDate stepDate = parseDate(stepEl.getChild("stepDate"));
      double stepValue = parseDecimal(stepEl.getChild("stepValue"));
      stepBuilder.add(ValueStep.of(stepDate, ValueAdjustment.ofReplace(stepValue)));
    }
    return ValueSchedule.of(initialValue, stepBuilder.build());
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'AdjustedRelativeDateOffset' to a resolved {@code LocalDate}.
   * 
   * @param baseEl  the FpML adjustable date element
   * @return the resolved date
   * @throws FpmlParseException if unable to parse
   */
  private AdjustableDate parseAdjustedRelativeDateOffset(XmlElement baseEl) {
    // FpML content: ('periodMultiplier', 'period', 'dayType?',
    //                'businessDayConvention', 'BusinessCentersOrReference.model?'
    //                'dateRelativeTo', 'adjustedDate', 'relativeDateAdjustments?')
    // The 'adjustedDate' element is ignored
    XmlElement relativeToEl = lookupReference(baseEl.getChild("dateRelativeTo"));
    LocalDate baseDate;
    if (relativeToEl.hasContent()) {
      baseDate = parseDate(relativeToEl);
    } else if (relativeToEl.getName().contains("relative")) {
      baseDate = parseAdjustedRelativeDateOffset(relativeToEl).adjusted();
    } else {
      throw new FpmlParseException(
          "Unable to resolve 'dateRelativeTo' to a date: " + baseEl.getChild("dateRelativeTo").getAttribute(HREF));
    }
    Period period = parsePeriod(baseEl);
    Optional<XmlElement> dayTypeEl = baseEl.getChildOptional("dayType");
    boolean calendarDays = period.isZero() || (dayTypeEl.isPresent() && dayTypeEl.get().getContent().equals("Calendar"));
    BusinessDayAdjustment bda1 = parseBusinessDayAdjustments(baseEl);
    BusinessDayAdjustment bda2 = baseEl.getChildOptional("relativeDateAdjustments")
        .map(el -> parseBusinessDayAdjustments(el))
        .orElse(bda1);
    // interpret and resolve, simple calendar arithmetic or business days
    LocalDate resolvedDate;
    if (period.getYears() > 0 || period.getMonths() > 0 || calendarDays) {
      resolvedDate = bda2.adjust(bda1.adjust(baseDate.plus(period)));
    } else {
      resolvedDate = bda2.adjust(bda1.adjust(bda1.getCalendar().shift(baseDate, period.getDays())));
    }
    return AdjustableDate.of(resolvedDate, bda2);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'RelativeDateOffset' to a {@code DaysAdjustment}.
   * 
   * @param baseEl  the FpML adjustable date element
   * @return the days adjustment
   * @throws FpmlParseException if the holiday calendar is not known
   */
  private DaysAdjustment parseRelativeDateOffsetDays(XmlElement baseEl) {
    // FpML content: ('periodMultiplier', 'period', 'dayType?',
    //                'businessDayConvention', 'BusinessCentersOrReference.model?'
    //                'dateRelativeTo', 'adjustedDate')
    // The 'dateRelativeTo' element is not used here
    // The 'adjustedDate' element is ignored
    Period period = parsePeriod(baseEl);
    if (period.toTotalMonths() != 0) {
      throw new FpmlParseException("Expected days-based period but found " + period);
    }
    Optional<XmlElement> dayTypeEl = baseEl.getChildOptional("dayType");
    boolean calendarDays = period.isZero() || (dayTypeEl.isPresent() && dayTypeEl.get().getContent().equals("Calendar"));
    BusinessDayConvention fixingBdc = FpmlConversions.businessDayConvention(
        baseEl.getChild("businessDayConvention").getContent());
    HolidayCalendar calendar = parseBusinessCenters(baseEl);
    if (calendarDays) {
      return DaysAdjustment.ofCalendarDays(
          period.getDays(), BusinessDayAdjustment.of(fixingBdc, calendar));
    } else {
      return DaysAdjustment.ofBusinessDays(period.getDays(), calendar);
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'AdjustableDate' to an {@code AdjustableDate}.
   * 
   * @param baseEl  the FpML adjustable date element
   * @return the adjustable date
   * @throws FpmlParseException if the holiday calendar is not known
   */
  private AdjustableDate parseAdjustableDate(XmlElement baseEl) {
    // FpML content: ('unadjustedDate', 'dateAdjustments', 'adjustedDate?')
    // The 'adjustedDate' element is ignored
    LocalDate unadjustedDate = parseDate(baseEl.getChild("unadjustedDate"));
    BusinessDayAdjustment adjustment = parseBusinessDayAdjustments(baseEl.getChild("dateAdjustments"));
    return AdjustableDate.of(unadjustedDate, adjustment);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'BusinessDayAdjustments' to a {@code BusinessDayAdjustment}.
   * 
   * @param baseEl  the FpML business centers or reference element to parse 
   * @return the business day adjustment
   * @throws FpmlParseException if the holiday calendar is not known
   */
  private BusinessDayAdjustment parseBusinessDayAdjustments(XmlElement baseEl) {
    // FpML content: ('businessDayConvention', 'BusinessCentersOrReference.model?')
    BusinessDayConvention bdc = FpmlConversions.businessDayConvention(
        baseEl.getChild("businessDayConvention").getContent());
    Optional<XmlElement> centersEl = baseEl.getChildOptional("businessCenters");
    Optional<XmlElement> centersRefEl = baseEl.getChildOptional("businessCentersReference");
    HolidayCalendar calendar = (centersEl.isPresent() || centersRefEl.isPresent() ?
        parseBusinessCenters(baseEl) : HolidayCalendars.NO_HOLIDAYS);
    return BusinessDayAdjustment.of(bdc, calendar);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'BusinessCentersOrReference.model' to a {@code HolidayCalendar}.
   * 
   * @param baseEl  the FpML business centers or reference element to parse 
   * @return the holiday calendar
   * @throws FpmlParseException if the holiday calendar is not known
   */
  private HolidayCalendar parseBusinessCenters(XmlElement baseEl) {
    // FpML content: ('businessCentersReference' | 'businessCenters')
    // FpML 'businessCenters' content: ('businessCenter+')
    // Each 'businessCenter' is a location treated as a holiday calendar
    Optional<XmlElement> optionalBusinessCentersEl = baseEl.getChildOptional("businessCenters");
    XmlElement businessCentersEl = optionalBusinessCentersEl.orElseGet(() ->
        lookupReference(baseEl.getChild("businessCentersReference")));
    HolidayCalendar calendar = HolidayCalendars.NO_HOLIDAYS;
    for (XmlElement businessCenterEl : businessCentersEl.getChildren("businessCenter")) {
      calendar = calendar.combineWith(parseBusinessCenter(businessCenterEl));
    }
    return calendar;
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'BusinessCenter' to a {@code HolidayCalendar}.
   * 
   * @param baseEl  the FpML calendar element to parse 
   * @return the calendar
   * @throws FpmlParseException if the calendar is not known
   */
  private HolidayCalendar parseBusinessCenter(XmlElement baseEl) {
    validateScheme(baseEl, "businessCenterScheme", "http://www.fpml.org/coding-scheme/business-center");
    return FpmlConversions.holidayCalendar(baseEl.getContent());
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'FloatingRateIndex.model' to an {@code Index}.
   * 
   * @param baseEl  the FpML floating rate index element to parse 
   * @return the index
   */
  private Index parseIndex(XmlElement baseEl) {
    List<Index> indexes = parseIndexes(baseEl);
    if (indexes.size() != 1) {
      throw new FpmlParseException("Expected one index but found  " + indexes.size());
    }
    return indexes.get(0);
  }

  /**
   * Converts an FpML 'FloatingRateIndex' with multiple tenors to an {@code Index}.
   * 
   * @param baseEl  the FpML floating rate index element to parse 
   * @return the index
   */
  private List<Index> parseIndexes(XmlElement baseEl) {
    XmlElement indexEl = baseEl.getChild("floatingRateIndex");
    validateScheme(indexEl, "floatingRateIndexScheme", "http://www.fpml.org/coding-scheme/floating-rate-index");
    FpmlFloatingRateIndex fpml = FpmlFloatingRateIndex.of(indexEl.getContent());
    List<XmlElement> tenorEls = baseEl.getChildren("indexTenor");
    if (tenorEls.isEmpty()) {
      return ImmutableList.of(fpml.toOvernightIndex());
    } else {
      return tenorEls.stream()
          .map(el -> {
            Period period = parsePeriod(el);
            return fpml.toIborIndex(period);
          })
          .collect(toImmutableList());
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'Period' to a {@code Period}.
   * 
   * @param baseEl  the FpML business centers or reference element to parse 
   * @return the period
   */
  private Period parsePeriod(XmlElement baseEl) {
    // FpML content: ('periodMultiplier', 'period')
    String multiplier = baseEl.getChild("periodMultiplier").getContent();
    String unit = baseEl.getChild("period").getContent();
    return Period.parse("P" + multiplier + unit);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML frequency to a {@code Frequency}.
   * 
   * @param baseEl  the FpML business centers or reference element to parse 
   * @return the frequency
   */
  private Frequency parseFrequency(XmlElement baseEl) {
    // FpML content: ('periodMultiplier', 'period')
    String multiplier = baseEl.getChild("periodMultiplier").getContent();
    String unit = baseEl.getChild("period").getContent();
    if (unit.equals("T")) {
      return Frequency.TERM;
    }
    return Frequency.parse(multiplier + unit);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'Money' to a {@code CurrencyAmount}.
   * 
   * @param baseEl  the FpML money element to parse 
   * @return the currency amount
   * @throws FpmlParseException if the currency is not known
   */
  private CurrencyAmount parseCurrencyAmount(XmlElement baseEl) {
    // FpML content: ('currency', 'amount')
    Currency currency = parseCurrency(baseEl.getChild("currency"));
    double amount = parseDecimal(baseEl.getChild("amount"));
    return CurrencyAmount.of(currency, amount);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'Currency' to a {@code Currency}.
   * 
   * @param baseEl  the FpML currency element to parse 
   * @return the currency
   * @throws FpmlParseException if the currency is not known
   */
  private Currency parseCurrency(XmlElement baseEl) {
    validateScheme(baseEl, "currencyScheme", "http://www.fpml.org/coding-scheme/external/iso4217");
    return Currency.of(baseEl.getContent());
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'DayCountFraction' to a {@code DayCount}.
   * 
   * @param baseEl  the FpML day count element to parse 
   * @return the day count
   * @throws FpmlParseException if the day count is not known
   */
  private DayCount parseDayCountFraction(XmlElement baseEl) {
    validateScheme(baseEl, "dayCountFractionScheme", "http://www.fpml.org/coding-scheme/day-count-fraction");
    return FpmlConversions.dayCount(baseEl.getContent());
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'decimal' to a {@code double}.
   * 
   * @param baseEl  the FpML element to parse 
   * @return the double
   * @throws FpmlParseException if the double is invalid
   */
  private double parseDecimal(XmlElement baseEl) {
    try {
      return Double.parseDouble(baseEl.getContent());
    } catch (NumberFormatException ex) {
      throw new FpmlParseException("Invalid number in '" + baseEl.getName() + "': " + ex.getMessage());
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML 'date' to a {@code LocalDate}.
   * 
   * @param baseEl  the FpML element to parse 
   * @return the date
   * @throws FpmlParseException if the date is invalid
   */
  private LocalDate parseDate(XmlElement baseEl) {
    try {
      return FpmlConversions.date(baseEl.getContent());
    } catch (DateTimeParseException ex) {
      throw new FpmlParseException("Invalid date in '" + baseEl.getName() + "': " + ex.getMessage());
    }
  }

  //-------------------------------------------------------------------------
  // validate that a specific element is not present
  private void validateNotPresent(XmlElement baseEl, String elementName) {
    if (baseEl.getChildOptional(elementName).isPresent()) {
      throw new FpmlParseException("Unsupported element: '" + elementName + "'");
    }
  }

  // validates that the scheme attribute is known
  private void validateScheme(XmlElement baseEl, String schemeAttr, String schemeValue) {
    if (baseEl.getAttributes().containsKey(schemeAttr)) {
      String scheme = baseEl.getAttribute(schemeAttr);
      if (!scheme.startsWith(schemeValue)) {
        throw new FpmlParseException("Unknown '" + schemeAttr + "' attribute value: " + scheme);
      }
    }
  }

  //-------------------------------------------------------------------------
  // locate our party href/id reference
  private String findOurParty(String ourParty) {
    if (ourParty == null) {
      return "";
    }
    for (Entry<String, String> entry : parties.entries()) {
      if (ourParty.equals(entry.getValue())) {
        return entry.getKey();
      }
    }
    throw new FpmlParseException(Messages.format(
        "Document does not contain our party ID: {} not found in {}", ourParty, parties));
  }

  // lookup an element via href/id reference
  private XmlElement lookupReference(XmlElement hrefEl) {
    String hrefId = hrefEl.getAttribute(HREF);
    XmlElement el = references.get(hrefId);
    if (el == null) {
      throw new FpmlParseException(Messages.format("Document reference not found: href='{}'", hrefId));
    }
    return el;
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    File dir = new File("E:/dev/strata/Strata/modules/function-beta/src/main/java/com/opengamma/strata/function/fpml");
    Stream.of(dir.listFiles())
        .filter(file -> file.getName().endsWith(".xml"))
        .peek(file -> System.out.println(file))
        .map(file -> com.google.common.io.Files.asByteSource(file))
        .forEach(source -> {
          try {
            List<Trade> trades = FpmlTradeParser.parseTrades(source, "PARTYAUS33");
            ProductTrade<Product> trade = (ProductTrade<Product>) trades.get(0);
            System.out.println(JodaBeanSer.PRETTY.xmlWriter().write((Bean) trade));
            //            ((Expandable<?>) trade.getProduct()).expand();
          } catch (FpmlParseException ex) {
            ex.printStackTrace(System.out);
          }
        });

    //    ResourceLocator res = ResourceLocator.of("classpath:com/opengamma/strata/function/fpml/ird-ex01-vanilla-swap.xml");
    ResourceLocator res = ResourceLocator.of("classpath:com/opengamma/strata/function/fpml/ird-ex32-zero-coupon-swap.xml");
    List<Trade> trades = FpmlTradeParser.parseTrades(res.getByteSource(), "PARTYAUS33");
    System.out.println(JodaBeanSer.PRETTY.xmlWriter().write((Bean) trades.get(0)));
  }

}
