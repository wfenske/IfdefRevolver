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

    public static boolean isAtLeastOneDayBefore(Date thisDate, Date otherDate) {
        return isAtLeastOneDayBefore(toCalendar(thisDate), toCalendar(otherDate));
    }

    public static Calendar toCalendar(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    public static boolean isAtLeastOneDayBefore(Calendar thisCal, Calendar otherCal) {
        int thisYear = thisCal.get(Calendar.YEAR);
        int otherYear = otherCal.get(Calendar.YEAR);

        if (thisYear < otherYear) return true;

        int thisDayOfYear = thisCal.get(Calendar.DAY_OF_YEAR);
        int otherDayOfYear = otherCal.get(Calendar.DAY_OF_YEAR);

        if (thisDayOfYear < otherDayOfYear) return true;

        return false;
    }
}
