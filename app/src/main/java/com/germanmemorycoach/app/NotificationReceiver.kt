package com.germanmemorycoach.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timeLabel = intent.getStringExtra("time_label") ?: "now"
        val slotIndex = intent.getIntExtra("slot_index", 0)
        val isFollowUp = intent.action == "ACTION_SESSION_FOLLOW_UP"

        val slotTitles = listOf("Learn & retrieve", "Reinforce", "Mixed retention", "Extra review")
        val sessionName = if (slotIndex in slotTitles.indices) slotTitles[slotIndex] else "Study session"

        val title = "INTO Vokabeltrainer"
        val body = if (isFollowUp) {
            "Reminder: $sessionName ($timeLabel) is waiting for you."
        } else {
            "Time for your $sessionName ($timeLabel)"
        }

        NotificationHelper.showNotification(
            context,
            title,
            body,
            if (isFollowUp) 3000 + slotIndex else 2000 + slotIndex
        )

        if (!isFollowUp) {
            NotificationHelper.scheduleFollowUpIfConfigured(context, slotIndex, timeLabel)
            NotificationHelper.rescheduleFromPrefs(context)
        }
    }
}
