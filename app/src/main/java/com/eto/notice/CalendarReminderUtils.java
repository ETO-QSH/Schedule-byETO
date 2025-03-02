package com.eto.notice;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.Toast;

public class CalendarReminderUtils {
    private static final String CALENDER_URL = "content://com.android.calendar/calendars";
    private static final String CALENDER_EVENT_URL = "content://com.android.calendar/events";
    private static final String CALENDER_REMINDER_URL = "content://com.android.calendar/reminders";
    private static final String CALENDARS_ACCOUNT_NAME = "2373204754@qq.com";
    private static final String CALENDARS_ACCOUNT_TYPE = "com.android.eto";
    private static final String CALENDARS_DISPLAY_NAME = "课程";
    private static final String CALENDARS_NAME = "ETO";

    /**
     * 检查是否已经添加了日历账户，如果没有添加先添加一个日历账户再查询
     * 获取账户成功返回账户id，否则返回-1
     */
    private static int checkAndAddCalendarAccount(Context context) {
        int oldId = checkCalendarAccount(context);
        if (oldId >= 0) {
            return oldId;
        } else {
            long addId = addCalendarAccount(context);
            if (addId >= 0) {
                return checkCalendarAccount(context);
            } else {
                return -1;
            }
        }
    }

    /**
     * 检查是否存在现有账户，存在则返回账户id，否则返回-1
     */
    @SuppressLint("Range")
    private static int checkCalendarAccount(Context context) {
        try (Cursor userCursor = context.getContentResolver().query(Uri.parse(CALENDER_URL), null, null, null, null)) {
            if (userCursor == null) {
                return -1;
            }
            int count = userCursor.getCount();
            if (count > 0) {
                userCursor.moveToFirst();
                return userCursor.getInt(userCursor.getColumnIndex(CalendarContract.Calendars._ID));
            } else {
                return -1;
            }
        }
    }

    /**
     * 添加日历账户，账户创建成功则返回账户id，否则返回-1
     */
    private static long addCalendarAccount(Context context) {
        TimeZone timeZone = TimeZone.getDefault();
        ContentValues value = new ContentValues();
        value.put(CalendarContract.Calendars.NAME, CALENDARS_NAME);
        value.put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDARS_ACCOUNT_NAME);
        value.put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDARS_ACCOUNT_TYPE);
        value.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDARS_DISPLAY_NAME);
        value.put(CalendarContract.Calendars.VISIBLE, 1);
        value.put(CalendarContract.Calendars.CALENDAR_COLOR, Color.RED);
        value.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
        value.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        value.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, timeZone.getID());
        value.put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDARS_ACCOUNT_NAME);
        value.put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 0);

        Uri calendarUri = Uri.parse(CALENDER_URL);
        calendarUri = calendarUri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDARS_ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDARS_ACCOUNT_TYPE)
                .build();

        Uri result = context.getContentResolver().insert(calendarUri, value);
        return result == null ? -1 : ContentUris.parseId(result);
    }

    /**
     * 添加日历事件
     */
    @SuppressLint("Range")
    public static void addCalendarEvent(Context context, String title, String locate, long startTime, long endMillis, int notice, String color, String description) {
        if (context == null) {
            return;
        }
        int calId = checkAndAddCalendarAccount(context); // 获取日历账户的id
        if (calId < 0) {
            return;
        }

        Calendar mCalendar = Calendar.getInstance();
        mCalendar.setTimeInMillis(startTime); // 设置开始时间
        long start = mCalendar.getTime().getTime();
        mCalendar.setTimeInMillis(endMillis); // 设置终止时间
        long end = mCalendar.getTime().getTime();

        ContentValues event = new ContentValues();

        event.put(CalendarContract.Events.TITLE, title);
        event.put(CalendarContract.Events.DESCRIPTION, description);
        event.put(CalendarContract.Events.CALENDAR_ID, calId);
        event.put(CalendarContract.Events.DTSTART, start);
        event.put(CalendarContract.Events.DTEND, end);
        event.put(CalendarContract.Events.HAS_ALARM, true);
        event.put(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Shanghai");
        event.put(CalendarContract.Events.EVENT_LOCATION, locate);
        event.put(CalendarContract.Events.EVENT_COLOR, Color.RED); // 设置颜色 未生效byETO

        // 保证事件长时间有效
        event.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
        event.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE);

        Uri newEvent = context.getContentResolver().insert(Uri.parse(CALENDER_EVENT_URL), event); // 添加事件
        if (newEvent == null) {
            return;
        }

        // 扩展属性
        Uri extendedPropUri = CalendarContract.ExtendedProperties.CONTENT_URI;
        extendedPropUri = extendedPropUri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDARS_ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDARS_ACCOUNT_TYPE).build();
        ContentValues extendedProperties = new ContentValues();
        extendedProperties.put(CalendarContract.ExtendedProperties.EVENT_ID,ContentUris.parseId(newEvent));
        extendedProperties.put(CalendarContract.ExtendedProperties.VALUE,"{\"need_alarm\":true}");
        extendedProperties.put(CalendarContract.ExtendedProperties.NAME,"agenda_info");
        Uri uriExtended = context.getContentResolver().insert(extendedPropUri, extendedProperties);
        if (uriExtended == null) { // 添加事件提醒失败直接返回
            return;
        }

        // 事件提醒的设定
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(newEvent));
        values.put(CalendarContract.Reminders.MINUTES, notice);
        values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT); // METHOD_DEFAULT | METHOD_ALERT
        Uri setValues = context.getContentResolver().insert(Uri.parse(CALENDER_REMINDER_URL), values);
        if (setValues == null) {
            Toast.makeText(context, "设置提示失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 删除日历事件
     */
    public static void deleteCalendarEvents(Context context) {
        int calId = checkAndAddCalendarAccount(context);
        if (calId < 0) return;

        Uri eventsUri = Uri.parse(CALENDER_EVENT_URL);
        String selection = CalendarContract.Events.CALENDAR_ID + "=?";
        String[] selectionArgs = new String[]{String.valueOf(calId)};

        try {
            int deletedRows = context.getContentResolver().delete(
                    eventsUri,
                    selection,
                    selectionArgs
            );
            Toast.makeText(context, "已删除" + deletedRows + "个事件", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(context, "需要日历权限", Toast.LENGTH_SHORT).show();
        }
    }
}