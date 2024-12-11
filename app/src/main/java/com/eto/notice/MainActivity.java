package com.eto.notice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String jsonStr = JsonReader.readJsonFromAsset(this, "test.json");

        try {
            // 解析JSON数据
            JSONObject jsonObject = new JSONObject(jsonStr);
            JSONArray eventsArray = jsonObject.getJSONArray("notice");

            // 遍历所有事件并添加到日历
            for (int i = 0; i < eventsArray.length(); i++) {
                JSONObject eventObject = eventsArray.getJSONObject(i);
                addCalendarEventFromJson(eventObject);
            }

            Toast.makeText(this, "所有日历事件添加成功", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "解析数据失败", Toast.LENGTH_SHORT).show();
        }
    }

    private long getTimeZoneOffsetMillis(String gmt) {
        try {
            // 提取时区偏移的数字部分，位于 "+" 或 "-" 符号之后
            String offsetStr = gmt.substring(1);
            // 解析为整数
            int offset = Integer.parseInt(offsetStr);
            // 如果原始字符串以 "+" 开头，则偏移量为正；如果以 "-" 开头，则偏移量为负
            return (long) (gmt.startsWith("+") ? 1 : -1) * offset * 60 * 60 * 1000;
        } catch (NumberFormatException e) {
            // 如果解析失败，返回标准UTC时区的偏移量，即0
            return 0;
        }
    }

    private void addCalendarEventFromJson(JSONObject jsonObject) {
        try {
            String title = jsonObject.getString("title");
            String start = jsonObject.getString("start");
            String end = jsonObject.getString("end");
            String day = jsonObject.getString("day");
            String gmt = jsonObject.getString("gmt");
            String locate = jsonObject.getString("locate");
            String color = jsonObject.getString("color");
            String data = jsonObject.getString("data");
            int notice = jsonObject.getInt("notice");

            // 解析日期和时间
            @SuppressLint("SimpleDateFormat") SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date date = dayFormat.parse(day);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT" + gmt));
            calendar.setTime(date);

            // 设置开始和结束时间
            @SuppressLint("SimpleDateFormat") SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            timeFormat.setTimeZone(calendar.getTimeZone());
            Date startTime = timeFormat.parse(start);
            Date endTime = timeFormat.parse(end);

            // 将开始和结束时间转换为毫秒时间戳
            long timeZoneOffsetMillis = getTimeZoneOffsetMillis(gmt);
            assert startTime != null;
            long startMillis = calendar.getTimeInMillis() + timeZoneOffsetMillis + startTime.getTime();
            assert endTime != null;
            long endMillis = calendar.getTimeInMillis() + timeZoneOffsetMillis + endTime.getTime();

            // 添加日历事件
            CalendarReminderUtils.addCalendarEvent(this, title, locate, startMillis, endMillis, notice, color,
                    "꒰ঌ( ⌯' '⌯)໒꒱" + data);

        } catch (ParseException | JSONException e) {
            Toast.makeText(this, "日期或时间格式错误qqq" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}