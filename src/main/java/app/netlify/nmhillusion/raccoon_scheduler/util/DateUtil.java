package app.netlify.nmhillusion.raccoon_scheduler.util;

import java.time.Month;

/**
 * date: 2022-11-18
 * <p>
 * created-by: nmhillusion
 */

public abstract class DateUtil {
    public static Month convertMonthFromShortNameOfMonth(String shortNameOfMonth) {
        return switch (String.valueOf(shortNameOfMonth).trim().substring(0, 3)) {
            case "Jan" ->
                    Month.JANUARY;
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
}
