package com.mapsupervision.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mapsupervision.app.MainActivity
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.DailyLog
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun dailyLogRepository(): com.mapsupervision.domain.repository.DailyLogRepository
    fun activeProjectRepository(): com.mapsupervision.domain.repository.ActiveProjectRepository
    fun workPlanRepository(): com.mapsupervision.domain.repository.WorkPlanRepository
}

class DiaryCalendarWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPoints.get(context.applicationContext, WidgetEntryPoint::class.java)
        val logRepository = entryPoint.dailyLogRepository()
        val activeProjectRepository = entryPoint.activeProjectRepository()
        val workPlanRepository = entryPoint.workPlanRepository()

        var activeProjectId: String? = null
        var dailyLogs: List<DailyLog> = emptyList()
        var workPlans: List<com.mapsupervision.domain.model.WorkPlan> = emptyList()

        val activeResult = activeProjectRepository.getActive()
        if (activeResult is AppResult.Success) {
            activeProjectId = activeResult.data
            if (!activeProjectId.isNullOrBlank()) {
                val logsResult = logRepository.byProject(activeProjectId)
                if (logsResult is AppResult.Success) {
                    dailyLogs = logsResult.data
                }
                val plansResult = workPlanRepository.byProject(activeProjectId)
                if (plansResult is AppResult.Success) {
                    workPlans = plansResult.data
                }
            }
        }

        provideContent {
            val prefs = androidx.glance.currentState<Preferences>()
            val today = Calendar.getInstance()
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val todayMonth = today.get(Calendar.MONTH)
            val todayYear = today.get(Calendar.YEAR)
            val currentMonth = prefs[CurrentMonthKey] ?: todayMonth
            val currentYear = prefs[CurrentYearKey] ?: todayYear
            val selectedDay = clampSelectedDay(
                prefs[SelectedDayKey] ?: today.get(Calendar.DAY_OF_MONTH),
                currentYear,
                currentMonth
            )

            val monthCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK)
            val emptyCellsBefore = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

            val selectedCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth)
                set(Calendar.DAY_OF_MONTH, selectedDay)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val logsForSelectedDay = dailyLogs.filter { log ->
                val logCal = Calendar.getInstance().apply { timeInMillis = log.createdAtEpochMs }
                logCal.get(Calendar.YEAR) == currentYear &&
                    logCal.get(Calendar.MONTH) == currentMonth &&
                    logCal.get(Calendar.DAY_OF_MONTH) == selectedDay
            }

            val selectedEpoch = java.time.LocalDate.of(currentYear, currentMonth + 1, selectedDay).toEpochDay()
            val plansForSelectedDay = workPlans.filter { it.plannedDateEpochDay == selectedEpoch }

            val monthShortLabel = "TH${currentMonth + 1}"
            val monthTitle = "THÁNG ${currentMonth + 1}"
            val selectedTitle = "$selectedDay $monthShortLabel"
            val selectedDaySummary = SimpleDateFormat("EEEE, dd/MM", Locale("vi", "VN"))
                .format(selectedCalendar.time)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("vi", "VN")) else it.toString() }

            val dayOfWeekStr = SimpleDateFormat("EEEE", Locale("vi", "VN")).format(selectedCalendar.time)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("vi", "VN")) else it.toString() }
            val selectedDayHeaderStr = "${SimpleDateFormat("dd/MM", Locale.US).format(selectedCalendar.time)} $dayOfWeekStr"

            val addIntent = Intent(context, MainActivity::class.java).apply {
                action = "ADD_DIARY"
                putExtra("date_millis", selectedCalendar.timeInMillis)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val widgetSize = LocalSize.current
            val isCompact = widgetSize.height < 160.dp

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                if (isCompact) {
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = GlanceModifier.defaultWeight()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = GlanceModifier
                                        .width(4.dp)
                                        .height(24.dp)
                                        .background(Color(0xFF8FD14F))
                                ) {}
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                Column {
                                    Text(
                                        text = selectedTitle,
                                        style = TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(Color.White),
                                            fontSize = 16.sp
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.height(1.dp))
                                    Text(
                                        text = selectedDaySummary,
                                        style = TextStyle(
                                            fontWeight = FontWeight.Normal,
                                            color = ColorProvider(Color(0xFFD7DBE8)),
                                            fontSize = 8.sp
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = GlanceModifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${logsForSelectedDay.size} nhật ký",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(Color.White),
                                        fontSize = 10.sp
                                    )
                                )
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                val summaryText = if (logsForSelectedDay.isEmpty()) {
                                    "Chưa có nhật ký"
                                } else {
                                    logsForSelectedDay.first().workItem.ifBlank { "Có nhật ký" }
                                }
                                Text(
                                    text = "• $summaryText",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Normal,
                                        color = ColorProvider(Color(0xFFB9C0D6)),
                                        fontSize = 9.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(12.dp))

                        Box(
                            modifier = GlanceModifier
                                .size(32.dp)
                                .background(Color(0x26FFFFFF))
                                .clickable(actionStartActivity(addIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color.White),
                                    fontSize = 18.sp
                                )
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDayHeaderStr,
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color.White),
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Box(
                            modifier = GlanceModifier
                                .clickable(actionStartActivity(addIntent))
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "+",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color.White),
                                    fontSize = 20.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .padding(8.dp)
                        ) {
                            Column(modifier = GlanceModifier.fillMaxSize()) {
                                Row(modifier = GlanceModifier.fillMaxWidth()) {
                                    listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { day ->
                                        Box(
                                            modifier = GlanceModifier.defaultWeight(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = day,
                                                style = TextStyle(
                                                    fontWeight = FontWeight.Bold,
                                                    color = ColorProvider(
                                                        if (day == "CN") Color(0xFFFF6B6B) else Color(0xFFD7DBE8)
                                                    ),
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = GlanceModifier.height(4.dp))

                                val totalCells = emptyCellsBefore + daysInMonth
                                val rows = (totalCells + 6) / 7
                                for (rowIndex in 0 until rows) {
                                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                                        for (colIndex in 0 until 7) {
                                            val cellIndex = rowIndex * 7 + colIndex
                                            if (cellIndex < emptyCellsBefore || cellIndex >= totalCells) {
                                                Box(modifier = GlanceModifier.defaultWeight()) {}
                                            } else {
                                                val dayNum = cellIndex - emptyCellsBefore + 1
                                                val isSelected = dayNum == selectedDay
                                                val isToday = dayNum == todayDay && currentMonth == todayMonth && currentYear == todayYear
                                                val hasLogs = dailyLogs.any { log ->
                                                    val logCal = Calendar.getInstance().apply { timeInMillis = log.createdAtEpochMs }
                                                    logCal.get(Calendar.YEAR) == currentYear &&
                                                        logCal.get(Calendar.MONTH) == currentMonth &&
                                                        logCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                                }

                                                val dayBgColor = when {
                                                    isSelected -> Color(0xFFF8F8FA)
                                                    isToday -> Color(0x33FFFFFF)
                                                    else -> Color.Transparent
                                                }

                                                Box(
                                                    modifier = GlanceModifier
                                                        .defaultWeight()
                                                        .padding(1.dp)
                                                        .cornerRadius(4.dp)
                                                        .background(dayBgColor)
                                                        .clickable(
                                                            actionRunCallback<SelectDayCallback>(
                                                                actionParametersOf(DayKey to dayNum)
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = dayNum.toString(),
                                                            style = TextStyle(
                                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                                color = ColorProvider(
                                                                    if (isSelected) Color(0xFF101725) else Color(0xFFD7DBE8)
                                                                ),
                                                                fontSize = 11.sp
                                                            )
                                                        )
                                                        if (hasLogs) {
                                                            Spacer(modifier = GlanceModifier.height(1.dp))
                                                            Box(
                                                                modifier = GlanceModifier
                                                                    .size(4.dp)
                                                                    .background(if (isSelected) Color(0xFF101725) else Color(0xFF8FD14F))
                                                            ) {}
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .padding(8.dp)
                        ) {
                            Column(modifier = GlanceModifier.fillMaxSize()) {
                                // --- Kế hoạch ---
                                Text(
                                    text = "Kế hoạch",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(Color(0xFF8FD14F)),
                                        fontSize = 11.sp
                                    )
                                )

                                Spacer(modifier = GlanceModifier.height(4.dp))

                                if (plansForSelectedDay.isEmpty()) {
                                    Text(
                                        text = "Không có kế hoạch",
                                        style = TextStyle(
                                            fontWeight = FontWeight.Normal,
                                            color = ColorProvider(Color(0xFFB9C0D6)),
                                            fontSize = 9.sp
                                        )
                                    )
                                } else {
                                    plansForSelectedDay.take(2).forEachIndexed { index, plan ->
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = plan.title.ifBlank { "Kế hoạch ${index + 1}" },
                                                    style = TextStyle(
                                                        fontWeight = FontWeight.Bold,
                                                        color = ColorProvider(Color.White),
                                                        fontSize = 9.sp
                                                    )
                                                )
                                                if (plan.description.isNotBlank()) {
                                                    Spacer(modifier = GlanceModifier.height(1.dp))
                                                    Text(
                                                        text = plan.description,
                                                        style = TextStyle(
                                                            fontWeight = FontWeight.Normal,
                                                            color = ColorProvider(Color(0xFFD7DBE8)),
                                                            fontSize = 8.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = GlanceModifier.height(10.dp))

                                // --- Nhật ký ---
                                Text(
                                    text = "Nhật ký",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(Color(0xFF8FD14F)),
                                        fontSize = 11.sp
                                    )
                                )

                                Spacer(modifier = GlanceModifier.height(4.dp))

                                if (logsForSelectedDay.isEmpty()) {
                                    Text(
                                        text = "Chưa có nhật ký",
                                        style = TextStyle(
                                            fontWeight = FontWeight.Normal,
                                            color = ColorProvider(Color(0xFFB9C0D6)),
                                            fontSize = 9.sp
                                        )
                                    )
                                } else {
                                    logsForSelectedDay.take(2).forEachIndexed { index, log ->
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = log.workItem.ifBlank { "Nhật ký ${index + 1}" },
                                                    style = TextStyle(
                                                        fontWeight = FontWeight.Bold,
                                                        color = ColorProvider(Color.White),
                                                        fontSize = 9.sp
                                                    )
                                                )
                                                Spacer(modifier = GlanceModifier.height(1.dp))
                                                Text(
                                                    text = buildSummaryLine(log),
                                                    style = TextStyle(
                                                        fontWeight = FontWeight.Normal,
                                                        color = ColorProvider(Color(0xFFD7DBE8)),
                                                        fontSize = 8.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val CurrentMonthKey = intPreferencesKey("current_month")
        val CurrentYearKey = intPreferencesKey("current_year")
        val SelectedDayKey = intPreferencesKey("selected_day")
        val DayKey = ActionParameters.Key<Int>("day_num")
    }
}

class SelectDayCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val day = parameters[DiaryCalendarWidget.DayKey] ?: return
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                set(DiaryCalendarWidget.SelectedDayKey, day)
            }
        }
        DiaryCalendarWidget().update(context, glanceId)
    }
}

class PrevMonthCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val month = prefs[DiaryCalendarWidget.CurrentMonthKey] ?: Calendar.getInstance().get(Calendar.MONTH)
            val year = prefs[DiaryCalendarWidget.CurrentYearKey] ?: Calendar.getInstance().get(Calendar.YEAR)
            val newMonth = if (month == 0) 11 else month - 1
            val newYear = if (month == 0) year - 1 else year
            val selectedDay = prefs[DiaryCalendarWidget.SelectedDayKey] ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            prefs.toMutablePreferences().apply {
                set(DiaryCalendarWidget.CurrentMonthKey, newMonth)
                set(DiaryCalendarWidget.CurrentYearKey, newYear)
                set(DiaryCalendarWidget.SelectedDayKey, clampSelectedDay(selectedDay, newYear, newMonth))
            }
        }
        DiaryCalendarWidget().update(context, glanceId)
    }
}

class NextMonthCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val month = prefs[DiaryCalendarWidget.CurrentMonthKey] ?: Calendar.getInstance().get(Calendar.MONTH)
            val year = prefs[DiaryCalendarWidget.CurrentYearKey] ?: Calendar.getInstance().get(Calendar.YEAR)
            val newMonth = if (month == 11) 0 else month + 1
            val newYear = if (month == 11) year + 1 else year
            val selectedDay = prefs[DiaryCalendarWidget.SelectedDayKey] ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            prefs.toMutablePreferences().apply {
                set(DiaryCalendarWidget.CurrentMonthKey, newMonth)
                set(DiaryCalendarWidget.CurrentYearKey, newYear)
                set(DiaryCalendarWidget.SelectedDayKey, clampSelectedDay(selectedDay, newYear, newMonth))
            }
        }
        DiaryCalendarWidget().update(context, glanceId)
    }
}

private fun clampSelectedDay(day: Int, year: Int, month: Int): Int {
    val maxDay = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)
    return day.coerceIn(1, maxDay)
}

private fun buildSummaryLine(log: DailyLog): String {
    val manpowerText = "${log.manpower} người"
    val weatherText = if (log.weather.isNotBlank()) log.weather else "Không có thời tiết"
    val extraText = if (log.note.isNotBlank()) log.note else weatherText
    return "$manpowerText • $extraText"
}
