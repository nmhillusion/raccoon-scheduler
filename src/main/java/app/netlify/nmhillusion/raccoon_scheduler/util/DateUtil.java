package app.netlify.nmhillusion.raccoon_scheduler.util;

import app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper;

import java.time.LocalDate;
import java.time.Month;

/**
 * date: 2022-11-18
 * <p>
 * created-by: nmhillusion
 */

public abstract class DateUtil {
    public static Month convertMonthFromShortNameOfMonth(String shortNameOfMonth) {
        final String trimmedMonth = StringUtil.trimWithNull(shortNameOfMonth);
        if (3 > trimmedMonth.length()) {
            return Month.JANUARY;
        }

        return switch (trimmedMonth.substring(0, 3)) {
            case "Feb" ->
                    Month.FEBRUARY;
            case "Mar" ->
                    Month.MARCH;
            case "Apr" ->
                    Month.APRIL;
            case "May" ->
                    Month.MAY;
            case "Jun" ->
                    Month.JUNE;
            case "Jul" ->
                    Month.JULY;
            case "Aug" ->
                    Month.AUGUST;
            case "Sep" ->
                    Month.SEPTEMBER;
            case "Oct" ->
                    Month.OCTOBER;
            case "Nov" ->
                    Month.NOVEMBER;
            case "Dec" ->
                    Month.DECEMBER;
            default ->
                    Month.JANUARY;
        };
    }

    public static boolean isLeapYear(int year) {
        if (0 == year % 4) {
            if (0 == year % 100) {
                return 0 == year % 400;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    public static LocalDate buildDateFromString(String day, String month, String year) {
        try {
            final Month month_ = DateUtil.convertMonthFromShortNameOfMonth(StringUtil.trimWithNull(month));

            day = StringUtil.trimWithNull(day);
            int day_ = 0 == day.length() ? 1 : Integer.parseInt(day);

            year = StringUtil.trimWithNull(year);
            final int year_ = 0 == year.length() ? 1 : Integer.parseInt(year);

            if (!isLeapYear(year_)) {
                if (Month.FEBRUARY.equals(month_)) {
                    day_ = Math.min(day_, 28);
                }
            }

            return LocalDate.of(year_, month_, day_);
        } catch (Exception ex) {
            LogHelper.getLog(DateUtil.class).error("MAIN", ex);
            return null;
        }
    }
}
