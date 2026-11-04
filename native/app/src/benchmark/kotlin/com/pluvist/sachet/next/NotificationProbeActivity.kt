package com.pluvist.sachet.next

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Only in the synthetic benchmark APK; does not instantiate MainActivity or repositories. */
class NotificationProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply {
            text = "正在验证课程通知\n请保持手机解锁"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
        })
        Log.i("CourseNotificationProbe", "host.onCreate")
    }

    override fun onResume() {
        super.onResume()
        Log.i("CourseNotificationProbe", "host.onResume")
    }
}
