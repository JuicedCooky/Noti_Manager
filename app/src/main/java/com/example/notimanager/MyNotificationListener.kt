package com.example.notimanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

class MyNotificationListener : NotificationListenerService() {

    private val notifKeyToGroupId = HashMap<String, String>()
    private val groupActiveKeys = HashMap<String, MutableSet<String>>()
    private val selfCancelledKeys = mutableSetOf<String>()
    private val sbnKeyToChildId = HashMap<String, Int>()
    private val childIdToSbnKey = HashMap<Int, String>()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Group Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (!isNotificationManagementEnabled(applicationContext)) return

        val groups = loadSavedGroups(applicationContext) ?: return
        val group = groups.firstOrNull { sbn.packageName in it.packageNames } ?: return
        if (!group.groupingEnabled) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: sbn.packageName
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        notifKeyToGroupId[sbn.key] = group.id
        groupActiveKeys.getOrPut(group.id) { mutableSetOf() }.add(sbn.key)

        val groupKey = "noti_manager_${group.id}"
        val childId = sbn.key.hashCode()
        sbnKeyToChildId[sbn.key] = childId
        childIdToSbnKey[childId] = sbn.key

        postChildNotification(childId, groupKey, group.name, group.icon, title, text)
        postSummaryNotification(group, groupActiveKeys[group.id] ?: emptySet(), groupKey)

        selfCancelledKeys.add(sbn.key)
        cancelNotification(sbn.key)

        Log.d(TAG, "posted | pkg=${sbn.packageName} | group=${group.name} | title=$title")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (selfCancelledKeys.remove(sbn.key)) return

        if (sbn.packageName == packageName) {
            val originalKey = childIdToSbnKey[sbn.id] ?: return
            cleanUpTracking(originalKey)
            return
        }

        val groupId = notifKeyToGroupId.remove(sbn.key) ?: return
        val childId = sbnKeyToChildId.remove(sbn.key)
        if (childId != null) {
            childIdToSbnKey.remove(childId)
            getSystemService(NotificationManager::class.java).cancel(childId)
        }
        val keys = groupActiveKeys[groupId] ?: return
        keys.remove(sbn.key)
        if (keys.isEmpty()) {
            groupActiveKeys.remove(groupId)
            getSystemService(NotificationManager::class.java).cancel(summaryId(groupId))
        } else {
            val group = loadSavedGroups(applicationContext)?.firstOrNull { it.id == groupId }
            if (group != null) postSummaryNotification(group, keys, "noti_manager_${groupId}")
        }
        Log.d(TAG, "removed | pkg=${sbn.packageName}")
    }

    private fun cleanUpTracking(originalKey: String) {
        val groupId = notifKeyToGroupId.remove(originalKey) ?: return
        val childId = sbnKeyToChildId.remove(originalKey)
        if (childId != null) childIdToSbnKey.remove(childId)
        val keys = groupActiveKeys[groupId] ?: return
        keys.remove(originalKey)
        if (keys.isEmpty()) {
            groupActiveKeys.remove(groupId)
            getSystemService(NotificationManager::class.java).cancel(summaryId(groupId))
        } else {
            val group = loadSavedGroups(applicationContext)?.firstOrNull { it.id == groupId }
            if (group != null) postSummaryNotification(group, keys, "noti_manager_${groupId}")
        }
    }

    private fun postChildNotification(childId: Int, groupKey: String, groupName: String, groupIcon: String, title: String, text: String) {
        val smallIcon = iconCompatFor(groupIcon)
            ?: IconCompat.createWithResource(this, android.R.drawable.ic_dialog_info)
        val style = NotificationCompat.BigTextStyle().bigText(text)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setAutoCancel(true)
        iconLargeBitmap(groupIcon, groupName)?.let { builder.setLargeIcon(it) }
        getSystemService(NotificationManager::class.java).notify(childId, builder.build())
    }


    private fun postSummaryNotification(group: SavedGroupData, activeKeys: Set<String>, groupKey: String) {
        val count = activeKeys.size
//        val smallIcon = iconCompatFor(group.icon)
//            ?: IconCompat.createWithResource(this, android.R.drawable.ic_dialog_info)

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(group.name) // Tells the system this is the group name
            .setSummaryText("$count new alerts")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a static icon
            .setContentTitle(group.name)
            .setContentText("${group.name} · $count alerts")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setStyle(inboxStyle) // InboxStyle often triggers better compliance from OEMs
            .setOnlyAlertOnce(true)

        getSystemService(NotificationManager::class.java).notify(summaryId(group.id), builder.build())
    }

    private fun iconLargeBitmap(iconName: String, groupName: String): Bitmap? {
        val resId = when (iconName) {
            "audio" -> R.drawable.audio
            "mail" -> R.drawable.mail
            "ic_launcher_foreground" -> R.drawable.ic_launcher_foreground
            else -> return null
        }
        val drawable = ContextCompat.getDrawable(this, resId)?.mutate() ?: return null
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgColor = AVATAR_COLORS[(groupName.firstOrNull()?.code ?: 0) % AVATAR_COLORS.size]
        canvas.drawCircle(size / 2f, size / 2f, size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
        drawable.setTint(android.graphics.Color.WHITE)
        val pad = size / 5
        drawable.setBounds(pad, pad, size - pad, size - pad)
        drawable.draw(canvas)
        return bitmap
    }

    private fun iconCompatFor(iconName: String): IconCompat? {
        val resId = when (iconName) {
            "audio" -> R.drawable.audio
            "mail" -> R.drawable.mail
            "ic_launcher_foreground" -> R.drawable.ic_launcher_foreground
            else -> return null
        }
        return IconCompat.createWithResource(this, resId)
    }

    private fun summaryId(groupId: String) = groupId.hashCode()

    companion object {
        private const val TAG = "NotiManager"
        private const val CHANNEL_ID = "noti_manager_groups"
        private val AVATAR_COLORS = intArrayOf(
            0xFF1565C0.toInt(), // blue
            0xFF2E7D32.toInt(), // green
            0xFF6A1B9A.toInt(), // purple
            0xFFC62828.toInt(), // red
            0xFFE65100.toInt(), // orange
            0xFF00695C.toInt(), // teal
        )
    }
}
