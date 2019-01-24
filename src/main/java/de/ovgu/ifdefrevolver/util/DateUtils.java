package de.ovgu.ifdefrevolver.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by wfenske on 12.04.17.
 */
public class DateUtils {
    /**
     * Prevent instantiation: This is supposed to be a collection of static helper functions
     */
    private DateUtils() {
    }

    /**
     * Test if the first date is at least one day before the the second date.
     * Hours, minutes, etc. are ignored in this comparison.
     *
     * @param thisDate
     * @param otherDate
     * @return
     */
    public static boolean isAtLeastOneDayBefore(Date thisDate, Date otherDate) {
        return isAtLeastOneDayBefore(toCalendar(thisDate), toCalendar(otherDate));
    }

    public static Calendar toCalendar(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    /**
     * Test if the first calendar is at least one day before the the second calendar.
     * Hours, minutes, etc. are ignored in this comparison.
     *
     * @param thisCal
     * @param otherCal
     * @return
     */
    public static boolean isAtLeastOneDayBefore(Calendar thisCal, Calendar otherCal) {
        int thisYear = thisCal.get(Calendar.YEAR);
        int otherYear = otherCal.get(Calendar.YEAR);

        if (thisYear < otherYear) return true;

        if (thisYear == otherYear) {
            int thisDayOfYear = thisCal.get(Calendar.DAY_OF_YEAR);
            int otherDayOfYear = otherCal.get(Calendar.DAY_OF_YEAR);

            if (thisDayOfYear < otherDayOfYear) return true;
        }

        return false;
    }
}
