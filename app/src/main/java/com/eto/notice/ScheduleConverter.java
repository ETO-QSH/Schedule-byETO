package com.eto.notice;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleConverter {

    private static final Map<String, ClassTime> CLASS_TIME = new HashMap<>();
    static {
        CLASS_TIME.put("第一节", new ClassTime("08:00", "08:45"));
        CLASS_TIME.put("第二节", new ClassTime("08:50", "09:35"));
        CLASS_TIME.put("第三节", new ClassTime("09:55", "10:40"));
        CLASS_TIME.put("第四节", new ClassTime("10:45", "11:30"));
        CLASS_TIME.put("第五节", new ClassTime("11:35", "12:20"));
        CLASS_TIME.put("第六节", new ClassTime("14:00", "14:45"));
        CLASS_TIME.put("第七节", new ClassTime("14:50", "15:35"));
        CLASS_TIME.put("第八节", new ClassTime("15:40", "16:25"));
        CLASS_TIME.put("第九节", new ClassTime("16:45", "17:30"));
        CLASS_TIME.put("第十节", new ClassTime("17:35", "18:20"));
        CLASS_TIME.put("第十一节", new ClassTime("19:00", "19:45"));
        CLASS_TIME.put("第十二节", new ClassTime("19:50", "20:35"));
        CLASS_TIME.put("第十三节", new ClassTime("20:40", "21:25"));
    }

    public static JSONObject convert(InputStream is) throws Exception {
        List<List<String>> classData = readExcel(is);
        Map<Integer, CourseInfo> classDict = parseClassData(classData);
            try {
                return buildResult(classDict);
            }  catch (Exception e) {
                throw new Exception("Excel解析失败: " + e.getMessage());
            }
    }

    private static List<List<String>> readExcel(InputStream is) throws Exception {
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        List<List<String>> dataList = new ArrayList<>();
        final int START_ROW = 7;  // C8单元格
        final int MAX_ROW = 39;
        final int START_COL = 2;  // C列

        for (int r = START_ROW; r <= MAX_ROW; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            // 按列遍历处理每个独立单元格
            for (int c = START_COL; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;

                String value = getCellValue(cell);
                if (value.isEmpty()) continue;

                // 精确分割逻辑（匹配Python）
                String[] courseBlocks = value.split("（本）");
                for (String block : courseBlocks) {
                    List<String> courseData = processCourseBlock(block.trim());
                    if (!courseData.isEmpty()) {
                        dataList.add(courseData);
                    }
                }
            }
        }
        return dataList;
    }

    private static List<String> processCourseBlock(String block) {
        List<String> result = new ArrayList<>();
        if (block.isEmpty()) return result;

        // 1. 提取课程标题
        String[] titleParts = block.split("\\s+", 2);
        if (titleParts.length < 1) return result;
        result.add(titleParts[0].trim());

        // 2. 提取周次信息
        Matcher weekMatcher = Pattern.compile("(\\d+[-\\d]*周)").matcher(block);
        List<String> weeks = new ArrayList<>();
        while (weekMatcher.find()) {
            weeks.add(weekMatcher.group(1));
        }
        result.addAll(weeks);

        // 3. 提取星期和节次
        Matcher timeMatcher = Pattern.compile("星期[1-7]").matcher(block);
        if (timeMatcher.find()) {
            result.add(timeMatcher.group());
        }

        Matcher classMatcher = Pattern.compile("([第][一二三四五六七八九十]+节-?)+").matcher(block);
        if (classMatcher.find()) {
            result.add(classMatcher.group());
        }

        // 4. 提取地点
        Matcher locationMatcher = Pattern.compile("余区\\S-[\\S-]+(?=,|$)").matcher(block);
        if (locationMatcher.find()) {
            result.add(locationMatcher.group().replace(",", ""));
        }
        return result.size() >= 5 ? result : Collections.emptyList();
    }

    private static Map<Integer, CourseInfo> parseClassData(List<List<String>> data) {
        Map<Integer, CourseInfo> map = new HashMap<>();
        Pattern rangePattern = Pattern.compile("(\\d+)-(\\d+)周");

        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            if (row.size() < 4) continue;

            // 解析课程标题
            String rawTitle = row.get(0);
            String title = rawTitle.split("-")[1].split("\\[")[0].trim();

            // 解析星期信息（取最后一位数字）
            String weekInfo = row.get(row.size() - 3);
            int weekday = Integer.parseInt(weekInfo.substring(weekInfo.length() - 1));

            List<Integer> days = new ArrayList<>();
            // 仅处理1到倒数第三列的数据
            for (int j = 1; j < row.size() - 3; j++) {
                String cell = row.get(j);
                Matcher matcher = rangePattern.matcher(cell);
                if (matcher.find()) {
                    int startWeek = Integer.parseInt(matcher.group(1));
                    int endWeek = Integer.parseInt(matcher.group(2));
                    // 修正为Python的range逻辑：range(startWeek-1, endWeek)
                    for (int week = startWeek - 1; week < endWeek; week++) {
                        days.add(week * 7 + weekday);
                    }
                } else if (cell.endsWith("周")) {
                    int week = Integer.parseInt(cell.substring(0, cell.length() - 1));
                    days.add((week - 1) * 7 + weekday); // 修正周数偏移
                }
            }

            // 解析课程节次
            String[] classes = row.get(row.size() - 2).split("-");
            String location = row.get(row.size() - 1);

            map.put(i, new CourseInfo(
                    title,
                    days,
                    Arrays.asList(classes),
                    location
            ));
        }
        return map;
    }

    private static JSONObject buildResult(Map<Integer, CourseInfo> data) throws JSONException {
        JSONObject root = new JSONObject();
        JSONArray entries = new JSONArray();

        // 基准日期设置
        Calendar baseDate = Calendar.getInstance();
        baseDate.set(2025, Calendar.FEBRUARY, 23);
        baseDate.setTimeZone(TimeZone.getTimeZone("GMT+8"));

        // 临时存储用于排序
        List<JSONObject> tempList = new ArrayList<>();

        for (CourseInfo info : data.values()) {
            for (int dayOffset : info.days) {
                // 每次创建新的Calendar实例
                Calendar calendar = (Calendar) baseDate.clone();
                calendar.add(Calendar.DAY_OF_YEAR, dayOffset);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                String dayStr = sdf.format(calendar.getTime());

                ClassTime startTime = CLASS_TIME.get(info.classes.get(0));
                ClassTime endTime = CLASS_TIME.get(info.classes.get(1));

                JSONObject entry = new JSONObject();
                entry.put("title", info.title);
                entry.put("start", startTime.start);
                entry.put("end", endTime.end);
                entry.put("day", dayStr);
                entry.put("gmt", "+8");
                entry.put("notice", 30);
                entry.put("color", "0xFFFFBFBF");
                entry.put("locate", info.location);
                entry.put("data", "");

                tempList.add(entry);
            }
        }

        // 精确排序实现
        tempList.sort((o1, o2) -> {
            try {
                int dateCompare = o1.getString("day").compareTo(o2.getString("day"));
                if (dateCompare != 0) return dateCompare;
                return o1.getString("start").compareTo(o2.getString("start"));
            } catch (JSONException e) {
                return 0;
            }
        });

        for (JSONObject entry : tempList) {
            entries.put(entry);
        }

        root.put("notice", entries);
        return root;
    }

    // 辅助类定义
    private static class ClassTime {
        final String start;
        final String end;

        ClassTime(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }

    static class CourseInfo {
        final String title;
        final List<Integer> days;
        final List<String> classes;
        final String location;

        CourseInfo(String title, List<Integer> days, List<String> classes, String location) {
            this.title = title;
            this.days = days;
            this.classes = classes;
            this.location = location;
        }
    }

    private static String getCellValue(Cell cell) {
        return cell.getStringCellValue().trim();
    }
}