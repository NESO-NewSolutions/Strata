/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.function.fpml;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.opengamma.strata.basics.date.BusinessDayConvention;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.schedule.RollConvention;
import com.opengamma.strata.collect.Messages;

/**
 * Loader of trade data in FpML format.
 * <p>
 * This handles the subset of FpML necessary to populate the trade model.
 */
public final class FpmlConversions {

  // FRN definition is dates that on same numerical day of month
  // Use last business day of month if no matching numerical date (eg. 31st June replaced by last business day of June)
  // Non-business days are replaced by following, or preceding to avoid changing the month
  // If last date was last business day of month, then all subsequent dates are last business day of month
  // While close to ModifiedFollowing, it is unclear is that is correct for BusinessDayConvention
  // FpML also has a 'NotApplicable' option, which probably should map to null in the caller
  /**
   * The FpML date parser.
   */
  private static final DateTimeFormatter FPML_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd[XXX]");

  /**
   * Restricted constructor.
   */
  private FpmlConversions() {
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML day count to a {@code DayCount}.
   * 
   * @param fpmlDayCountName  the day count name used by FpML
   * @return the day count
   * @throws FpmlParseException if the day count is not known
   */
  public static DayCount dayCount(String fpmlDayCountName) {
    return DayCount.extendedEnum().externalNames("FpML").lookup(fpmlDayCountName);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML business day convention to a {@code BusinessDayConvention}.
   * 
   * @param fmplBusinessDayConventionName  the business day convention name used by FpML
   * @return the business day convention
   * @throws FpmlParseException if the business day convention is not known
   */
  public static BusinessDayConvention businessDayConvention(String fmplBusinessDayConventionName) {
    return BusinessDayConvention.extendedEnum().externalNames("FpML").lookup(fmplBusinessDayConventionName);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML roll convention to a {@code RollConvention}.
   * 
   * @param fmplRollConventionName  the roll convention name used by FpML
   * @return the roll convention
   * @throws FpmlParseException if the roll convention is not known
   */
  public static RollConvention rollConvention(String fmplRollConventionName) {
    return RollConvention.extendedEnum().externalNames("FpML").lookup(fmplRollConventionName);
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML business center to a {@code HolidayCalendar}.
   * 
   * @param fpmlBusinessCenter  the business center name used by FpML
   * @return the holiday calendar
   * @throws FpmlParseException if the holiday calendar is not known
   */
  public static HolidayCalendar holidayCalendar(String fpmlBusinessCenter) {
    try {
      return HolidayCalendar.of(fpmlBusinessCenter);
    } catch (IllegalArgumentException ex) {
      throw new FpmlParseException(Messages.format("Unknown FpML business center: {}", fpmlBusinessCenter));
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Converts an FpML date to a {@code LocalDate}.
   * 
   * @param dateStr  the business center name used by FpML
   * @return the holiday calendar
   * @throws DateTimeParseException if the date cannot be parsed
   */
  public static LocalDate date(String dateStr) {
    return LocalDate.parse(dateStr, FPML_DATE_FORMAT);
  }

}
