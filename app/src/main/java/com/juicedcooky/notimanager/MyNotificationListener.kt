package com.juicedcooky.notimanager

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (!isNotificationManagementEnabled(applicationContext)) return

        if (isIgnoreMediaAndOngoing(applicationContext)) {
            val flags = sbn.notification.flags
            val category = sbn.notification.category
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 || category == Notification.CATEGORY_TRANSPORT) return
        }

        val groups = loadSavedGroups(applicationContext) ?: return
        val group = groups.firstOrNull { sbn.packageName in it.packageNames } ?: return
        if (!group.notificationsEnabled) {
            selfCancelledKeys.add(sbn.key)
            cancelNotification(sbn.key)
            return
        }
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(summaryId(groupId))
            nm.cancel(anchorId(groupId))
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(summaryId(groupId))
            nm.cancel(anchorId(groupId))
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
            ?: IconCompat.createWithResource(this, R.drawable.noti_manager_foreground)
        val emoji = iconEmoji(group.icon)
        val displayName = if (emoji.isNotEmpty()) "$emoji ${group.name}:" else group.name
        val accentColor = group.dotColor?.toArgb()
            ?: AVATAR_COLORS[(group.name.firstOrNull()?.code ?: 0) % AVATAR_COLORS.size]
        val nm = getSystemService(NotificationManager::class.java)

        // Visible summary: no setGroupSummary so Samsung renders it as a normal notification.
        // setSortKey("!") places it above children (lexicographically first); setWhen ensures
        // it is the most-recent notification so Samsung's recency ordering also puts it on top.
//        val inboxStyle = NotificationCompat.InboxStyle().setSummaryText("$count updates available")
        val visBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setColor(accentColor)
            .setColorized(false)
            .setContentTitle(displayName)
            .setContentText("$count updates available")
            .setGroup(groupKey)
            .setSortKey("!")
            .setWhen(System.currentTimeMillis())
//            .setStyle(inboxStyle)
            .setOnlyAlertOnce(true)
        iconLargeBitmap(group.icon, group.name, group.dotColor)?.let { visBuilder.setLargeIcon(it) }
        nm.notify(summaryId(group.id), visBuilder.build())

        // Minimal group anchor: required for Android's group-collapsing machinery on stock
        // Android. Samsung typically hides this entirely, which is the desired behavior.
        val anchorBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(displayName)
            .setContentText("$count updates available")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setSortKey("~")
            .setOnlyAlertOnce(true)
        nm.notify(anchorId(group.id), anchorBuilder.build())
    }

    private fun iconEmoji(iconName: String) = when (iconName) {
        "android" -> "🤖"
        "apartment" -> "🏢"
        "apps" -> "📱"
        "audio" -> "🔊"
        "bluetooth" -> "🔵"
        "cake" -> "🎂"
        "call" -> "📞"
        "cases" -> "💼"
        "chat" -> "💬"
        "cleaning_services" -> "🧹"
        "cloud" -> "☁️"
        "cloud_upload" -> "📤"
        "credit_card" -> "💳"
        "database" -> "🗄️"
        "description" -> "📄"
        "desktop_windows" -> "🖥️"
        "directions_bike" -> "🚲"
        "directions_car" -> "🚗"
        "dnd" -> "🚫"
        "emoticon" -> "😊"
        "explore" -> "🧭"
        "favourite" -> "💝"
        "flight" -> "✈️"
        "folder" -> "📁"
        "globe" -> "🌐"
        "headphones" -> "🎧"
        "heart" -> "❤️"
        "inbox" -> "📥"
        "key" -> "🔑"
        "list" -> "📋"
        "location_home" -> "🏠"
        "mail" -> "✉️"
        "man" -> "👨"
        "map" -> "🗺️"
        "memory" -> "💾"
        "menu" -> "📖"
        "mic" -> "🎤"
        "mobile" -> "📱"
        "mood" -> "😄"
        "movie" -> "🎬"
        "music" -> "🎵"
        "navigation" -> "➡️"
        "news" -> "📰"
        "notifications" -> "🔔"
        "package_2" -> "📦"
        "pet_supplies" -> "🐾"
        "photo" -> "🖼️"
        "receipt" -> "🧾"
        "restaurant" -> "🍽️"
        "school" -> "🏫"
        "sentiment_dissatisfied" -> "😞"
        "sentiment_satisfied" -> "😊"
        "settings" -> "⚙️"
        "shopping_bag" -> "🛍️"
        "shopping_basket" -> "🧺"
        "shopping_cart" -> "🛒"
        "sports_basketball" -> "🏀"
        "sports_esports" -> "🎮"
        "star" -> "⭐"
        "store" -> "🏪"
        "storefront" -> "🏬"
        "thumb_up" -> "👍"
        "trophy" -> "🏆"
        "videocam" -> "📹"
        "wallet" -> "👜"
        "watch" -> "⌚"
        "wifi" -> "📶"
        "woman" -> "👩"
        else -> ""
    }


    private fun iconLargeBitmap(iconName: String, groupName: String, dotColor: Color? = null): Bitmap? {
        val resId = when (iconName) {
            "android" -> R.drawable.android
            "apartment" -> R.drawable.apartment
            "apps" -> R.drawable.apps
            "audio" -> R.drawable.audio
            "bluetooth" -> R.drawable.bluetooth
            "cake" -> R.drawable.cake
            "call" -> R.drawable.call
            "cases" -> R.drawable.cases
            "chat" -> R.drawable.chat
            "cleaning_services" -> R.drawable.cleaning_services
            "cloud" -> R.drawable.cloud
            "cloud_upload" -> R.drawable.cloud_upload
            "credit_card" -> R.drawable.credit_card
            "database" -> R.drawable.database
            "description" -> R.drawable.description
            "desktop_windows" -> R.drawable.desktop_windows
            "directions_bike" -> R.drawable.directions_bike
            "directions_car" -> R.drawable.directions_car
            "dnd" -> R.drawable.dnd
            "emoticon" -> R.drawable.emoticon
            "explore" -> R.drawable.explore
            "favourite" -> R.drawable.favourite
            "flight" -> R.drawable.flight
            "folder" -> R.drawable.folder
            "globe" -> R.drawable.globe
            "headphones" -> R.drawable.headphones
            "heart" -> R.drawable.heart
            "inbox" -> R.drawable.inbox
            "key" -> R.drawable.key
            "list" -> R.drawable.list
            "location_home" -> R.drawable.location_home
            "mail" -> R.drawable.mail
            "man" -> R.drawable.man
            "map" -> R.drawable.map
            "memory" -> R.drawable.memory
            "menu" -> R.drawable.menu
            "mic" -> R.drawable.mic
            "mobile" -> R.drawable.mobile
            "mood" -> R.drawable.mood
            "movie" -> R.drawable.movie
            "music" -> R.drawable.music
            "navigation" -> R.drawable.navigation
            "news" -> R.drawable.news
            "notifications" -> R.drawable.notifications
            "package_2" -> R.drawable.package_2
            "pet_supplies" -> R.drawable.pet_supplies
            "photo" -> R.drawable.photo
            "receipt" -> R.drawable.receipt
            "restaurant" -> R.drawable.restaurant
            "school" -> R.drawable.school
            "sentiment_dissatisfied" -> R.drawable.sentiment_dissatisfied
            "sentiment_satisfied" -> R.drawable.sentiment_satisfied
            "settings" -> R.drawable.settings
            "shopping_bag" -> R.drawable.shopping_bag
            "shopping_basket" -> R.drawable.shopping_basket
            "shopping_cart" -> R.drawable.shopping_cart
            "sports_basketball" -> R.drawable.sports_basketball
            "sports_esports" -> R.drawable.sports_esports
            "star" -> R.drawable.star
            "store" -> R.drawable.store
            "storefront" -> R.drawable.storefront
            "thumb_up" -> R.drawable.thumb_up
            "trophy" -> R.drawable.trophy
            "videocam" -> R.drawable.videocam
            "wallet" -> R.drawable.wallet
            "watch" -> R.drawable.watch
            "wifi" -> R.drawable.wifi
            "woman" -> R.drawable.woman
            "ic_launcher_foreground" -> R.drawable.noti_manager_foreground
            else -> return null
        }
        val drawable = ContextCompat.getDrawable(this, resId)?.mutate() ?: return null
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgColor = dotColor?.toArgb()
            ?: AVATAR_COLORS[(groupName.firstOrNull()?.code ?: 0) % AVATAR_COLORS.size]
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
            "android" -> R.drawable.android
            "apartment" -> R.drawable.apartment
            "apps" -> R.drawable.apps
            "audio" -> R.drawable.audio
            "bluetooth" -> R.drawable.bluetooth
            "cake" -> R.drawable.cake
            "call" -> R.drawable.call
            "cases" -> R.drawable.cases
            "chat" -> R.drawable.chat
            "cleaning_services" -> R.drawable.cleaning_services
            "cloud" -> R.drawable.cloud
            "cloud_upload" -> R.drawable.cloud_upload
            "credit_card" -> R.drawable.credit_card
            "database" -> R.drawable.database
            "description" -> R.drawable.description
            "desktop_windows" -> R.drawable.desktop_windows
            "directions_bike" -> R.drawable.directions_bike
            "directions_car" -> R.drawable.directions_car
            "dnd" -> R.drawable.dnd
            "emoticon" -> R.drawable.emoticon
            "explore" -> R.drawable.explore
            "favourite" -> R.drawable.favourite
            "flight" -> R.drawable.flight
            "folder" -> R.drawable.folder
            "globe" -> R.drawable.globe
            "headphones" -> R.drawable.headphones
            "heart" -> R.drawable.heart
            "inbox" -> R.drawable.inbox
            "key" -> R.drawable.key
            "list" -> R.drawable.list
            "location_home" -> R.drawable.location_home
            "mail" -> R.drawable.mail
            "man" -> R.drawable.man
            "map" -> R.drawable.map
            "memory" -> R.drawable.memory
            "menu" -> R.drawable.menu
            "mic" -> R.drawable.mic
            "mobile" -> R.drawable.mobile
            "mood" -> R.drawable.mood
            "movie" -> R.drawable.movie
            "music" -> R.drawable.music
            "navigation" -> R.drawable.navigation
            "news" -> R.drawable.news
            "notifications" -> R.drawable.notifications
            "package_2" -> R.drawable.package_2
            "pet_supplies" -> R.drawable.pet_supplies
            "photo" -> R.drawable.photo
            "receipt" -> R.drawable.receipt
            "restaurant" -> R.drawable.restaurant
            "school" -> R.drawable.school
            "sentiment_dissatisfied" -> R.drawable.sentiment_dissatisfied
            "sentiment_satisfied" -> R.drawable.sentiment_satisfied
            "settings" -> R.drawable.settings
            "shopping_bag" -> R.drawable.shopping_bag
            "shopping_basket" -> R.drawable.shopping_basket
            "shopping_cart" -> R.drawable.shopping_cart
            "sports_basketball" -> R.drawable.sports_basketball
            "sports_esports" -> R.drawable.sports_esports
            "star" -> R.drawable.star
            "store" -> R.drawable.store
            "storefront" -> R.drawable.storefront
            "thumb_up" -> R.drawable.thumb_up
            "trophy" -> R.drawable.trophy
            "videocam" -> R.drawable.videocam
            "wallet" -> R.drawable.wallet
            "watch" -> R.drawable.watch
            "wifi" -> R.drawable.wifi
            "woman" -> R.drawable.woman
            "ic_launcher_foreground" -> R.drawable.noti_manager_foreground
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
    private fun anchorId(groupId: String) = groupId.hashCode() xor Int.MIN_VALUE

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
