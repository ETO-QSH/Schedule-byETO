package com.eto.notice;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PICK_JSON = 1;
    private ImageView loadingGif;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化权限管理器
        permissionManager = new PermissionManager(this);

        // 检查权限状态
        if (!permissionManager.hasAllPermissions()) {
            showPermissionExplanationDialog();
        } else {
            initializeApp();
        }
    }

    private void showPermissionExplanationDialog() {
        // 创建自定义样式对话框
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission, null);

        // 初始化视图组件
        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView messageView = dialogView.findViewById(R.id.dialog_message);
        Button positiveButton = dialogView.findViewById(R.id.btn_positive);
        Button negativeButton = dialogView.findViewById(R.id.btn_negative);

        // 设置内容
        titleView.setText("权限申请说明");
        messageView.setText("本应用需要以下权限\n日历权限：添加管理课程提醒");

        // 创建对话框
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // 设置按钮点击事件
        positiveButton.setOnClickListener(v -> {
            permissionManager.checkAndRequestPermissions();
            dialog.dismiss();
        });

        negativeButton.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        // 显示对话框
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (permissionManager.handlePermissionsResult(requestCode, grantResults)) {
            initializeApp();
        } else {
            Toast.makeText(this, "权限缺失，无法使用", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeApp() {
        // 初始化UI组件
        Button btnSelectFile = findViewById(R.id.btn_select_file);
        Button btnLoginEvents = findViewById(R.id.btn_login_events);
        Button btnClearEvents = findViewById(R.id.btn_clear_events);

        loadingGif = findViewById(R.id.loading_gif);

        // 初始化GIF（使用Glide）
        Glide.with(this)
                .asGif()
                .load(R.raw.loading_anim)
                .into(loadingGif);

        // 设置点击监听
        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnLoginEvents.setOnClickListener(v -> loginTaskEvents());
        btnClearEvents.setOnClickListener(v -> clearCalendarEvents());
    }

    // 节点定义
    private final int[] NODES = {0, 5, 15, 30, 60};

    private int snapToNode(int progress) {
        int closest = NODES[0];
        int minDiff = Math.abs(progress - closest);
        for (int node : NODES) {
            int diff = Math.abs(progress - node);
            if (diff < minDiff) {
                closest = node;
                minDiff = diff;
            }
        }
        return closest;
    }

    private void loginTaskEvents() {
        // 创建自定义对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login_setting, null);
        builder.setView(dialogView);

        // 设置标题
        View titleView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_title, null);

        TextView titleText = titleView.findViewById(R.id.alertTitle);
        titleText.setText("统一身份验证");
        titleText.setTypeface(ResourcesCompat.getFont(this, R.font.lolita));

        builder.setCustomTitle(titleView);

        // 获取布局组件
        EditText etUsername = dialogView.findViewById(R.id.et_username);
        EditText etPassword = dialogView.findViewById(R.id.et_password);

        // SeekBar 初始化
        SeekBar sbMinutes = dialogView.findViewById(R.id.sb_minutes);
        TextView tvMinutes = dialogView.findViewById(R.id.tv_minutes);

        // 初始进度设为 30，并吸附到最近的节点
        int initialProgress = 30;
        int snappedInitial = snapToNode(initialProgress);
        sbMinutes.setProgress(snappedInitial);
        tvMinutes.setText(String.format(Locale.CHINA, "%2s min", snappedInitial));

        // 滑动监听
        sbMinutes.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int snappedProgress = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                snappedProgress = snapToNode(progress);
                String text = String.format(Locale.CHINA, "%2s min", snappedProgress);
                tvMinutes.setText(text);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(snappedProgress);
            }
        });

        // 设置对话框按钮
        builder.setPositiveButton("确定", (dialog, which) -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            int minutes = sbMinutes.getProgress();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "账号密码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 执行登录逻辑
            performLogin(username, password, minutes);
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // 对话框显示后修改按钮字体
        dialog.getWindow().getDecorView().post(() -> {
            Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTypeface(ResourcesCompat.getFont(this, R.font.lolita));
                positiveButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            }

            Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTypeface(ResourcesCompat.getFont(this, R.font.lolita));
                negativeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            }
        });

        // 自动弹出软键盘
        etUsername.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etUsername, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private LoginTask loginTask;
    private Handler timeoutHandler;

    private void performLogin(String username, String password, int minutes) {
        showLoading();

        // 超时配置（30 秒）
        final int TIMEOUT_MILLIS = 30 * 1000;
        timeoutHandler = new Handler();
        Runnable timeoutRunnable = () -> {
            if (loginTask != null && !loginTask.isCancelled()) {
                loginTask.cancel(true);
                hideLoading();
                Toast.makeText(MainActivity.this, "登录超时，请检查网络连接", Toast.LENGTH_SHORT).show();
            }
        };

        loginTask = new LoginTask(username, password, minutes, new LoginTask.LoginCallback() {
            @Override
            public void onLoginSuccess(JSONObject json) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                Toast.makeText(MainActivity.this, "登录成功，将设置提前" + minutes + "分钟提醒", Toast.LENGTH_SHORT).show();
                new ProcessJsonTask().execute(json);
            }

            @Override
            public void onLoginFailure(String err) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                hideLoading();
                if (Objects.equals(err, "null loginResponseHtml")) {
                    Toast.makeText(MainActivity.this, "登录失败，学校服务器崩了", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "登录失败，账号密码错误", Toast.LENGTH_SHORT).show();
                }
            }
        });
        loginTask.execute();
        // 启动超时检测
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MILLIS);
    }

    // 新增处理JSON的异步任务
    @SuppressLint("StaticFieldLeak")
    private class ProcessJsonTask extends AsyncTask<JSONObject, Void, Integer> {
        @Override
        protected Integer doInBackground(JSONObject... jsonObjects) {
            try {
                return processJsonData(jsonObjects[0]);
            } catch (Exception e) {
                return -1;
            }
        }

        @Override
        protected void onPostExecute(Integer count) {
            hideLoading();
            if (count > 0) {
                Toast.makeText(MainActivity.this, "成功添加"+count+"个事件", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "数据处理失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // 同时支持JSON和Excel文件
        intent.setType("*/*");
        String[] mimeTypes = {"application/json", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        // 尝试设置初始目录
        Uri initialUri = getInitialDirectoryUri();
        intent.putExtra("android.provider.extra.INITIAL_URI", initialUri);
        startActivityForResult(intent, REQUEST_CODE_PICK_JSON);
    }

    private Uri getInitialDirectoryUri() {
        // 优先尝试获取Downloads目录
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                return DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:" + Environment.DIRECTORY_DOWNLOADS
                );
            }
        }

        // 备用方案：使用应用私有目录
        return DocumentsContract.buildDocumentUri(
                getPackageName(),
                getFilesDir().getAbsolutePath()
        );
    }

    private void clearCalendarEvents() {
        CalendarReminderUtils.deleteCalendarEvents(this);
        Toast.makeText(this, "日历事件已清除", Toast.LENGTH_SHORT).show();
    }

    @Override
    @SuppressLint("StaticFieldLeak")
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_JSON && resultCode == RESULT_OK && data != null) {
            showLoading();
            new AsyncTask<Uri, Void, Integer>() {
                @Override
                protected Integer doInBackground(Uri... uris) {
                    try (InputStream is = getContentResolver().openInputStream(uris[0])) {
                        String mimeType = getContentResolver().getType(uris[0]);
                        if (mimeType != null) {
                            if (mimeType.startsWith("application/json")) {
                                return processJsonFile(uris[0]);
                            } else if (mimeType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){
                                return processExcelFile(is);
                            }
                        }
                        return 0;
                    } catch (Exception e) {
                        return -1;
                    }
                }

                @Override
                protected void onPostExecute(Integer count) {
                    hideLoading();
                    if (count > 0) {
                        Toast.makeText(MainActivity.this, "成功添加"+count+"个事件", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "文件处理失败", Toast.LENGTH_SHORT).show();
                    }
                }

                private int processJsonFile(Uri uri) {
                    try {
                        String jsonStr = readJsonFromUri(uri);
                        JSONObject json = new JSONObject(jsonStr);
                        return processJsonData(json);
                    } catch (Exception e) {
                        return -1;
                    }
                }

                private int processExcelFile(InputStream is) {
                    try {
                        JSONObject json = ScheduleConverter.convert(is); // 标注
                        return processJsonData(json);
                    } catch (Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this, "错误：" + e.getMessage(), Toast.LENGTH_LONG).show());
                        return -1;
                    }
                }
            }.execute(data.getData());
        }
    }

    private void showLoading() {
        runOnUiThread(() -> {
            loadingGif.setVisibility(View.VISIBLE);
            Glide.with(MainActivity.this)
                    .asGif()
                    .load(R.raw.loading_anim)
                    .into(loadingGif);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            loadingGif.setVisibility(View.GONE);
            Glide.with(MainActivity.this).clear(loadingGif);
        });
    }

    private String readJsonFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            runOnUiThread(() ->
                    Toast.makeText(this, "JSON读取失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return null;
        }
    }

    private int processJsonData(JSONObject json) {
        try {
            JSONArray eventsArray = json.getJSONArray("notice");
            for (int i = 0; i < eventsArray.length(); i++) {
                JSONObject eventObject = eventsArray.getJSONObject(i);
                addCalendarEventFromJson(eventObject);
            }
            return eventsArray.length();
        } catch (JSONException e) {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "JSON格式错误", Toast.LENGTH_SHORT).show());
            return -1;
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
            Toast.makeText(this, "日期或时间格式错误" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}