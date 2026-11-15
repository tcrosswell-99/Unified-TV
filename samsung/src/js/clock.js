/*
 * Unified TV for Samsung Frame: the UK clock (spec 7.2): 24-hour, Europe/London, no seconds.
 *
 * Uses the browser's own time-zone data when it has it; otherwise works out British Summer Time itself (from 01:00 UTC
 * on the last Sunday of March to 01:00 UTC on the last Sunday of October), so the time is right even on an engine
 * without full Intl support. The TV's own clock must be right for either to be right.
 */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else { root.UTV = root.UTV || {}; root.UTV.Clock = api; }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  function two(n) { return (n < 10 ? '0' : '') + n; }
  function lastSunday(year, month) {
    const d = new Date(Date.UTC(year, month + 1, 0, 1, 0, 0));
    d.setUTCDate(d.getUTCDate() - d.getUTCDay());
    return d.getTime();
  }
  /** True when the UK is on British Summer Time at this instant. */
  function summerTime(ms) {
    const year = new Date(ms).getUTCFullYear();
    return ms >= lastSunday(year, 2) && ms < lastSunday(year, 9);
  }
  /** "HH:mm" in the UK, worked out without Intl. */
  function manual(ms, hour12) {
    const d = new Date(ms + (summerTime(ms) ? 3600000 : 0));
    let h = d.getUTCHours();
    const m = two(d.getUTCMinutes());
    if (!hour12) return two(h) + ':' + m;
    const suffix = h < 12 ? 'am' : 'pm';
    h = h % 12; if (h === 0) h = 12;
    return h + ':' + m + ' ' + suffix;
  }
  let formatter;
  function intl(ms) {
    if (formatter === undefined) {
      try {
        formatter = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/London', hour: '2-digit', minute: '2-digit', hour12: false });
        if (!/^\d\d:\d\d$/.test(formatter.format(0))) formatter = null;
      } catch (e) { formatter = null; }
    }
    return formatter ? formatter.format(ms) : null;
  }
  /** The time shown on Home, on alarm entries and on Wallpaper. hour12 is a Wallpaper option only. */
  function format(ms, options) {
    const hour12 = !!(options && options.hour12);
    if (!hour12 && !(options && options.manual)) {
      const value = intl(ms);
      if (value) return value.replace(/^24:/, '00:');
    }
    return manual(ms, hour12);
  }
  /** "Sunday 25 October", for the optional Wallpaper date. */
  const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  function date(ms) {
    const d = new Date(ms + (summerTime(ms) ? 3600000 : 0));
    return DAYS[d.getUTCDay()] + ' ' + d.getUTCDate() + ' ' + MONTHS[d.getUTCMonth()];
  }
  /** Milliseconds until the next minute begins, so the clock changes exactly on the minute. */
  function untilNextMinute(ms) { return 60000 - (ms % 60000); }

  return { format: format, manual: manual, summerTime: summerTime, date: date, untilNextMinute: untilNextMinute };
}));
