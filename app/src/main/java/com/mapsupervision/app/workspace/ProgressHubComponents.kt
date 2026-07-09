package com.mapsupervision.app.workspace

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.mapsupervision.core.ui.theme.SecondaryMint
import com.mapsupervision.core.ui.theme.PrimaryPeach
import com.mapsupervision.core.ui.theme.SuccessColor
import com.mapsupervision.core.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfrastructureItem(
    title: String,
    subtitle: String = "",
    id: String,
    supervisor: String,
    planProgress: Float,
    actualProgress: Float,
    borderColor: Color,
    planColor: Color,
    actualColor: Color,
    isWarning: Boolean,
    isNew: Boolean = false,
    onClick: () -> Unit = {}
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(borderColor))
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                        if (isNew) {
                            Text(
                                "Chưa cập nhật",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (subtitle.isNotBlank() && subtitle != title) {
                            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        if (isWarning) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("ID: $id - Supervisor: $supervisor", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PLAN", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.width(50.dp))
                    LinearProgressIndicator(
                        progress = { planProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = SecondaryMint,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACTUAL", color = PrimaryPeach, fontSize = 10.sp, modifier = Modifier.width(50.dp))
                    LinearProgressIndicator(
                        progress = { actualProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = PrimaryPeach,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineItem(
    time: String,
    content: @Composable () -> Unit,
    iconContent: @Composable () -> Unit,
    isLast: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            iconContent()
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 24.dp)) {
            Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

fun nodeDisplayName(nodeCode: String, nodesMap: Map<String, com.mapsupervision.domain.model.GisNode>): String {
    val label = nodesMap[nodeCode]?.mapNumberLabel
    return when {
        !label.isNullOrBlank() -> label
        nodeCode.isNotBlank()  -> nodeCode
        else                   -> "Node không xác định"
    }
}

fun estimatedDelayDays(variance: Float): Int = (variance * 30f / 100f).roundToInt()

fun formatRelativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    return when {
        cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) ->
            "Hôm nay, $timeStr"
        cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) - 1 ->
            "Hôm qua, $timeStr"
        else ->
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}

// Task 2: Summary stats composables
@Composable
fun SummaryStatsRow(
    totalNodes: Int,
    updatedNodes: Int,
    onTrackNodes: Int,
    delayedNodes: Int,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    successColor: Color,
    orangeColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(label = "Tổng node", value = totalNodes.toString(), valueColor = primaryTextColor, modifier = Modifier.weight(1f))
        StatCard(label = "Đã cập nhật", value = updatedNodes.toString(), valueColor = successColor, modifier = Modifier.weight(1f))
        StatCard(label = "Đúng tiến độ", value = onTrackNodes.toString(), valueColor = successColor, modifier = Modifier.weight(1f))
        StatCard(label = "Chậm", value = delayedNodes.toString(), valueColor = orangeColor, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    GlassmorphicCard(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = valueColor)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun ShimmerItem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}
