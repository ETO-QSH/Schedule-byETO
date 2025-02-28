package com.eto.notice;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class PermissionManager {
    // 定义所有需要请求的权限
    public static final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
    };

    private final Activity activity;
    private final int REQUEST_CODE = 1001;

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    // 检查并请求缺失的权限
    public void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                    missingPermissions.toArray(new String[0]),
                    REQUEST_CODE);
        }
    }

    // 处理权限请求结果
    public boolean handlePermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    // 检查是否所有权限都已授予
    public boolean hasAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
