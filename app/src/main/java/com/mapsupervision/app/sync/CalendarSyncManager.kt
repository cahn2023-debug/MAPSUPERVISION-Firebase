package com.mapsupervision.app.sync

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.DailyLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasCalendarPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    private fun getDefaultCalendarId(): Long? {
        if (!hasCalendarPermissions()) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val uri = CalendarContract.Calendars.CONTENT_URI
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri, projection, null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                
                var primaryId: Long? = null
                var fallbackId: Long? = null
                
                do {
                    val id = cursor.getLong(idCol)
                    val isPrimary = cursor.getInt(primaryCol) == 1
                    if (isPrimary) {
                        primaryId = id
                        break
                    }
                    if (fallbackId == null) {
                        fallbackId = id
                    }
                } while (cursor.moveToNext())
                
                return primaryId ?: fallbackId
            }
        } catch (e: Exception) {
            AppLogger.d("CalendarSyncManager.getDefaultCalendarId error: ${e.message}")
        } finally {
            cursor?.close()
        }
        return null
    }

    fun syncDailyLogToCalendar(log: DailyLog) {
        if (!hasCalendarPermissions()) return
        val calendarId = getDefaultCalendarId() ?: return
        
        try {
            // Delete existing event for this diary log if exists to avoid duplicates
            deleteExistingEvent(log.id)
            
            // Insert event
            val startMillis = log.createdAtEpochMs
            val endMillis = startMillis + 30 * 60 * 1000 // 30 minutes duration
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "[Nhật ký] ${log.workItem}")
                put(
                    CalendarContract.Events.DESCRIPTION,
                    "Vị trí: ${log.nodeCode ?: "Chưa xác định"}\n" +
                    "Nhân công: ${log.manpower} người\n" +
                    "Thời tiết: ${log.weather} (${log.temperature}°C)\n" +
                    "Ghi chú: ${log.note}\n" +
                    "Khối lượng: ${log.volume} ${log.unit}\n" +
                    "AppLogId: ${log.id}"
                )
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            AppLogger.d("CalendarSyncManager.syncDailyLogToCalendar success for logId: ${log.id}")
        } catch (e: Exception) {
            AppLogger.d("CalendarSyncManager.syncDailyLogToCalendar error: ${e.message}")
        }
    }

    private fun deleteExistingEvent(logId: String) {
        if (!hasCalendarPermissions()) return
        val uri = CalendarContract.Events.CONTENT_URI
        val selection = "${CalendarContract.Events.DESCRIPTION} LIKE ?"
        val selectionArgs = arrayOf("%AppLogId: $logId%")
        try {
            context.contentResolver.delete(uri, selection, selectionArgs)
        } catch (e: Exception) {
            AppLogger.d("CalendarSyncManager.deleteExistingEvent error: ${e.message}")
        }
    }

    fun fetchCalendarEventsForMonth(year: Int, month: Int): List<SystemEvent> {
        val eventsList = mutableListOf<SystemEvent>()
        if (!hasCalendarPermissions()) return eventsList
        
        val startCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = startCal.timeInMillis
            add(Calendar.MONTH, 1)
        }
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val selectionArgs = arrayOf(startCal.timeInMillis.toString(), endCal.timeInMillis.toString())
        
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val descCol = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                
                do {
                    val title = cursor.getString(titleCol) ?: ""
                    val desc = cursor.getString(descCol) ?: ""
                    val dtstart = cursor.getLong(startCol)
                    
                    // Filter out events that were created by our own app so they aren't duplicated as separate suggestions
                    if (!desc.contains("AppLogId:")) {
                        eventsList.add(SystemEvent(title, desc, dtstart))
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            AppLogger.d("CalendarSyncManager.fetchCalendarEventsForMonth error: ${e.message}")
        } finally {
            cursor?.close()
        }
        return eventsList
    }
}

data class SystemEvent(
    val title: String,
    val description: String,
    val timeInMillis: Long
)
