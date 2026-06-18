package com.example.notimanager

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "noti_groups"
private const val KEY_GROUPS = "groups_json"
private const val KEY_MANAGEMENT_ENABLED = "management_enabled"

data class SavedGroupData(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val dotColor: Color?,
    val dotScale: Float,
    val center: Offset,
    val packageNames: List<String>,
    val groupingEnabled: Boolean = true,
    val headsUpEnabled: Boolean = false
)

fun saveGroups(context: Context, groups: List<GroupState>) {
    val arr = JSONArray()
    for (g in groups) {
        val obj = JSONObject()
        obj.put("id", g.id)
        obj.put("name", g.name)
        obj.put("description", g.description)
        obj.put("icon", g.icon)
        obj.put("dotScale", g.dotScale.toDouble())
        // Store internal ULong representation as a Long (bit-preserving round-trip).
        if (g.dotColor != null) obj.put("dotColor", g.dotColor!!.value.toLong())
        obj.put("groupingEnabled", g.groupingEnabled)
        obj.put("headsUpEnabled", g.headsUpEnabled)
        obj.put("centerX", g.center.x.toDouble())
        obj.put("centerY", g.center.y.toDouble())
        val pkgs = JSONArray()
        for (app in g.apps) pkgs.put(app.packageName)
        obj.put("packages", pkgs)
        arr.put(obj)
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_GROUPS, arr.toString()).apply()
}

fun loadSavedGroups(context: Context): List<SavedGroupData>? {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_GROUPS, null) ?: return null
    return try {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            SavedGroupData(
                id = obj.getString("id"),
                name = obj.getString("name"),
                description = obj.optString("description", ""),
                icon = obj.optString("icon", ""),
                dotColor = if (obj.has("dotColor")) Color(obj.getLong("dotColor").toULong()) else null,
                dotScale = obj.optDouble("dotScale", 1.0).toFloat(),
                groupingEnabled = obj.optBoolean("groupingEnabled", true),
                headsUpEnabled = obj.optBoolean("headsUpEnabled", false),
                center = Offset(
                    obj.getDouble("centerX").toFloat(),
                    obj.getDouble("centerY").toFloat()
                ),
                packageNames = run {
                    val pkgs = obj.getJSONArray("packages")
                    List(pkgs.length()) { j -> pkgs.getString(j) }
                }
            )
        }
    } catch (e: Exception) {
        null  // corrupted prefs — start fresh
    }
}

fun isNotificationManagementEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_MANAGEMENT_ENABLED, true)

fun setNotificationManagementEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_MANAGEMENT_ENABLED, enabled).apply()
}

private const val KEY_GLOBAL_HEADSUP = "global_headsup_enabled"

fun isGlobalHeadsUpEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_GLOBAL_HEADSUP, false)

fun setGlobalHeadsUpEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_GLOBAL_HEADSUP, enabled).apply()
}

private const val KEY_IGNORE_MEDIA_ONGOING = "ignore_media_and_ongoing"

fun isIgnoreMediaAndOngoing(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_IGNORE_MEDIA_ONGOING, true)

fun setIgnoreMediaAndOngoing(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_IGNORE_MEDIA_ONGOING, enabled).apply()
}

private const val KEY_APP_BUBBLE_SCALE = "app_bubble_scale"
private const val KEY_APP_SPACING_SCALE = "app_spacing_scale"

fun getAppBubbleScale(context: Context): Float =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getFloat(KEY_APP_BUBBLE_SCALE, 1.0f)

fun setAppBubbleScale(context: Context, value: Float) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putFloat(KEY_APP_BUBBLE_SCALE, value).apply()
}

fun getAppSpacingScale(context: Context): Float =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getFloat(KEY_APP_SPACING_SCALE, 1.0f)

fun setAppSpacingScale(context: Context, value: Float) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putFloat(KEY_APP_SPACING_SCALE, value).apply()
}
