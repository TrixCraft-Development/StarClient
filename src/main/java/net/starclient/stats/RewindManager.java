package net.starclient.stats;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Manages the schedule, active window, and popup state for the StarClient "Rewind of the Year".
 * Both the trigger date and the active duration (in days) are configured via the constants below.
 */
public final class RewindManager {

    // =========================================================================
    // REWIND SCHEDULE CONFIGURATION (Set via code)
    // =========================================================================

    /**
     * How many days the Yearly Rewind remains active starting from the trigger date.
     * During this window, the Rewind buttons are shown on the Main Menu and in the Stats Menu.
     */
    public static final int REWIND_DURATION_DAYS = 7;

    /**
     * Recurring End-of-Year start month (1 = January ... 12 = December) and day (1-31).
     * Default: December 25 (active for 7 days through December 31).
     */
    public static final int END_OF_YEAR_MONTH = 12;
    public static final int END_OF_YEAR_DAY = 25;

    /**
     * Custom trigger date set in code (YYYY, MM, DD).
     * Set to a specific LocalDate to trigger the Rewind starting on that date for REWIND_DURATION_DAYS,
     * or set to null to only use the recurring End-of-Year schedule above.
     */
    public static final LocalDate SET_START_DATE = null;

    // =========================================================================

    private static final RewindManager INSTANCE = new RewindManager();

    private boolean popupShownThisLaunch = false;
    private boolean watchedThisSession = false;

    private RewindManager() {
    }

    public static RewindManager getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the active start date if today falls within the configured Rewind window
     * (either the custom SET_START_DATE or the recurring End-of-Year date), or null if inactive.
     */
    public LocalDate getActiveStartDate() {
        return getActiveStartDate(LocalDate.now());
    }

    public LocalDate getActiveStartDate(LocalDate today) {
        if (today == null) return null;

        // 1. Check custom set date in code
        if (SET_START_DATE != null) {
            long diff = ChronoUnit.DAYS.between(SET_START_DATE, today);
            if (diff >= 0 && diff < REWIND_DURATION_DAYS) {
                return SET_START_DATE;
            }
        }

        // 2. Check recurring End-of-Year date for current year (and previous year if window spans Jan 1)
        try {
            LocalDate eoyCurrent = LocalDate.of(today.getYear(), END_OF_YEAR_MONTH, END_OF_YEAR_DAY);
            long diffCurrent = ChronoUnit.DAYS.between(eoyCurrent, today);
            if (diffCurrent >= 0 && diffCurrent < REWIND_DURATION_DAYS) {
                return eoyCurrent;
            }
        } catch (Exception ignored) {
        }

        try {
            LocalDate eoyPrev = LocalDate.of(today.getYear() - 1, END_OF_YEAR_MONTH, END_OF_YEAR_DAY);
            long diffPrev = ChronoUnit.DAYS.between(eoyPrev, today);
            if (diffPrev >= 0 && diffPrev < REWIND_DURATION_DAYS) {
                return eoyPrev;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Returns true if the current date is within the configured Rewind window (e.g. 7 days from start date).
     */
    public boolean isRewindActive() {
        return getActiveStartDate() != null;
    }

    /**
     * Returns the number of days remaining in the active Rewind window (1 .. REWIND_DURATION_DAYS).
     */
    public int getDaysRemaining() {
        LocalDate today = LocalDate.now();
        LocalDate start = getActiveStartDate(today);
        if (start == null) {
            return 0;
        }
        long elapsed = ChronoUnit.DAYS.between(start, today);
        return (int) Math.max(1L, REWIND_DURATION_DAYS - elapsed);
    }

    /**
     * Returns the year associated with the current Rewind.
     */
    public int getRewindYear() {
        LocalDate start = getActiveStartDate();
        return start != null ? start.getYear() : LocalDate.now().getYear();
    }

    /**
     * Returns true if the initial launch popup ("Your rewind of the year is here...") should open.
     */
    public synchronized boolean shouldShowInitialPopup() {
        return isRewindActive() && !this.popupShownThisLaunch;
    }

    /**
     * Marks the initial launch popup as shown so it does not re-open when navigating back to the Main Menu.
     */
    public synchronized void markPopupShown() {
        this.popupShownThisLaunch = true;
    }

    public synchronized boolean hasWatchedThisSession() {
        return this.watchedThisSession;
    }

    public synchronized void markWatched() {
        this.watchedThisSession = true;
        this.popupShownThisLaunch = true;
    }
}
