package com.homestudio.local

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

/**
 * Multi-waypoint continuous touch drag instrumentation helper for Samsung One UI Home.
 * Usage:
 *   adb shell am instrument -w -e class com.homestudio.local.GestureRunnerTest#dragPath \
 *     -e waypoints "665,955;665,120;166,1353" -e holds "850,700,150" \
 *     com.homestudio.local.test/androidx.test.runner.AndroidJUnitRunner
 */
class GestureRunnerTest {

    @Test
    fun dragPath() {
        val args = InstrumentationRegistry.getArguments()
        val rawPts = args.getString("waypoints") ?: return
        val rawHolds = args.getString("holds") ?: ""
        val moveStepMs = (args.getString("stepMs") ?: "16").toLong().coerceIn(8L, 40L)
        val segmentDurationMs = (args.getString("segMs") ?: "320").toLong().coerceIn(80L, 1500L)

        val points = rawPts.split(";").mapNotNull { seg ->
            val parts = seg.split(",")
            if (parts.size == 2) parts[0].trim().toFloat() to parts[1].trim().toFloat() else null
        }
        if (points.size < 2) return

        val holds = rawHolds.split(",").mapNotNull { it.trim().toLongOrNull() }
        val uiAuto = InstrumentationRegistry.getInstrumentation().uiAutomation

        fun sendEvent(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
            val ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            uiAuto.injectInputEvent(ev, true)
            ev.recycle()
        }

        val downTime = SystemClock.uptimeMillis()
        var now = downTime
        var curX = points[0].first
        var curY = points[0].second

        sendEvent(MotionEvent.ACTION_DOWN, downTime, now, curX, curY)
        val initialHold = holds.getOrElse(0) { 800L }
        val holdSteps = (initialHold / 40L).toInt().coerceAtLeast(1)
        repeat(holdSteps) {
            SystemClock.sleep(40L)
            now = SystemClock.uptimeMillis()
            sendEvent(MotionEvent.ACTION_MOVE, downTime, now, curX, curY)
        }

        for (i in 1 until points.size) {
            val targetX = points[i].first
            val targetY = points[i].second
            val steps = (segmentDurationMs / moveStepMs).toInt().coerceAtLeast(6)
            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                val x = curX + (targetX - curX) * t
                val y = curY + (targetY - curY) * t
                SystemClock.sleep(moveStepMs)
                now = SystemClock.uptimeMillis()
                sendEvent(MotionEvent.ACTION_MOVE, downTime, now, x, y)
            }
            curX = targetX
            curY = targetY

            val holdAtNode = holds.getOrElse(i) { 120L }
            if (holdAtNode > 0) {
                val nHolds = (holdAtNode / 40L).toInt().coerceAtLeast(1)
                repeat(nHolds) {
                    SystemClock.sleep(40L)
                    now = SystemClock.uptimeMillis()
                    sendEvent(MotionEvent.ACTION_MOVE, downTime, now, curX, curY)
                }
            }
        }

        now = SystemClock.uptimeMillis()
        sendEvent(MotionEvent.ACTION_UP, downTime, now, curX, curY)
        SystemClock.sleep(250L)
    }
}
