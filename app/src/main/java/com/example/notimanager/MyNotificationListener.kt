package com.example.notimanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.pm.PackageManager
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Group Notifications", NotificationManager.IMPORTANCE_DEFAULT
            ))
            nm.createNotificationChannel(NotificationChannel(
                HEADSUP_CHANNEL_ID, "Group Heads-up Notifications", NotificationManager.IMPORTANCE_HIGH
            ))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (!isNotificationManagementEnabled(applicationContext)) return

        if (isIgnoreMediaAndOngoing(applicationContext)) {
            val flags = sbn.notification.flags
            val category = sbn.notification.category
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 || category == Notification.CATEGORY_TRANSPORT) return
        }

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

        val effectiveHeadsUp = group.headsUpEnabled && isGlobalHeadsUpEnabled(applicationContext)
        postChildNotification(childId, groupKey, group.name, group.icon, sbn.packageName, sbn.notification.contentIntent, effectiveHeadsUp, title, text)
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

    private fun postChildNotification(childId: Int, groupKey: String, groupName: String, groupIcon: String, sourcePackage: String, contentIntent: PendingIntent?, headsUpEnabled: Boolean, title: String, text: String) {
        val smallIcon = iconCompatFor(groupIcon)
            ?: IconCompat.createWithResource(this, android.R.drawable.ic_dialog_info)

        val appIconBitmap = try {
            packageManager.getApplicationIcon(sourcePackage).toBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        val channelId = if (headsUpEnabled) HEADSUP_CHANNEL_ID else CHANNEL_ID
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle("$title:")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setGroup(groupKey)
            .setGroupAlertBehavior(
                if (headsUpEnabled) NotificationCompat.GROUP_ALERT_CHILDREN
                else NotificationCompat.GROUP_ALERT_SUMMARY
            )
            .setPriority(
                if (headsUpEnabled) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
        contentIntent?.let { builder.setContentIntent(it) }
        appIconBitmap?.let { builder.setLargeIcon(it) }
        getSystemService(NotificationManager::class.java).notify(childId, builder.build())
    }


    private fun postSummaryNotification(group: SavedGroupData, activeKeys: Set<String>, groupKey: String) {
        val count = activeKeys.size
        val smallIcon = iconCompatFor(group.icon)
            ?: IconCompat.createWithResource(this, R.drawable.ic_launcher_foreground)
        val emoji = iconEmoji(group.icon)
        val displayName = if (emoji.isNotEmpty()) "$emoji ${group.name}" else group.name

        val inboxStyle = NotificationCompat.InboxStyle()
            .setSummaryText("$displayName ($count)")

        val accentColor = AVATAR_COLORS[(group.name.firstOrNull()?.code ?: 0) % AVATAR_COLORS.size]

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setColor(accentColor)
            .setColorized(false)
            .setContentTitle(displayName)
            .setContentText("$count updates available")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setStyle(inboxStyle)
            .setOnlyAlertOnce(true)
        iconLargeBitmap(group.icon, group.name)?.let { builder.setLargeIcon(it) }

        getSystemService(NotificationManager::class.java).notify(summaryId(group.id), builder.build())
    }

    private fun iconEmoji(iconName: String) = when (iconName) {
        "audio" -> "🔊"
        "mail" -> "✉️"
        else -> ""
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

    private fun android.graphics.drawable.Drawable.toBitmap(): Bitmap {
        val w = if (intrinsicWidth > 0) intrinsicWidth else 96
        val h = if (intrinsicHeight > 0) intrinsicHeight else 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp
    }

    private fun summaryId(groupId: String) = groupId.hashCode()

    companion object {
        private const val TAG = "NotiManager"
        private const val CHANNEL_ID = "noti_manager_groups"
        private const val HEADSUP_CHANNEL_ID = "noti_manager_headsup"
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
