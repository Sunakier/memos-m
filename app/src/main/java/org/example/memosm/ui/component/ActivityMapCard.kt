package org.example.memosm.ui.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.UserStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ActivityMapCard(
    userStats: UserStats?,
    modifier: Modifier = Modifier
) {
    val timestamps = userStats?.memoDisplayTimestamps ?: emptyList()
    Log.d("ActivityMapCard", "ActivityMapCard: Received ${timestamps.size} timestamps")
    if (timestamps.isNotEmpty()) {
        Log.d("ActivityMapCard", "ActivityMapCard: First timestamp: ${timestamps.first()}")
        Log.d("ActivityMapCard", "ActivityMapCard: Last timestamp: ${timestamps.last()}")
    }
    
    val activityData = remember(timestamps) {
        calculateActivityDataFromTimestamps(timestamps)
    }
    
    val totalActivities = activityData.values.sum()
    
    // Find the date range to display - use the month with most recent activity or current month
    val displayMonth = remember(activityData) {
        if (activityData.isEmpty()) {
            YearMonth.now()
        } else {
            val latestDate = activityData.keys.maxOrNull() ?: LocalDate.now()
            YearMonth.from(latestDate)
        }
    }
    
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profile_activity_map),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.profile_activity_count, totalActivities),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Calendar view for the month with activity
            CalendarMonthView(
                yearMonth = displayMonth,
                activityData = activityData,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Legend (color only)
            ActivityLegend()
        }
    }
}

@Composable
private fun CalendarMonthView(
    yearMonth: YearMonth,
    activityData: Map<LocalDate, Int>,
    modifier: Modifier = Modifier
) {
    val firstDayOfMonth = yearMonth.atDay(1)
    val lastDayOfMonth = yearMonth.atEndOfMonth()
    val today = LocalDate.now()
    
    // Calculate max for normalization
    val maxCount = activityData.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    Column(modifier = modifier) {
        // Month and year header
        Text(
            text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Weekday headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val daysOfWeek = listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
            )
            for (day in daysOfWeek) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Calendar grid - days of month
        val firstDayOfWeek = if (firstDayOfMonth.dayOfWeek == DayOfWeek.SUNDAY) 0 
                             else firstDayOfMonth.dayOfWeek.value
        val daysInMonth = lastDayOfMonth.dayOfMonth
        val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7
        val numWeeks = totalCells / 7
        
        for (week in 0 until numWeeks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (dayOfWeek in 0..6) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayOfMonth = cellIndex - firstDayOfWeek + 1
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayOfMonth in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayOfMonth)
                            val count = activityData[date] ?: 0
                            val isToday = date == today
                            
                            // Calculate intensity
                            val intensity = calculateIntensity(count, maxCount)
                            
                            val cellColor = when {
                                count > 0 -> lerp(surfaceVariant, primaryColor, intensity)
                                else -> surfaceVariant.copy(alpha = 0.5f)
                            }
                            
                            val textColor = when {
                                count > 0 -> MaterialTheme.colorScheme.onPrimary
                                isToday -> primaryColor
                                else -> onSurfaceVariant.copy(alpha = 0.8f)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(cellColor)
                                    .then(
                                        if (isToday && count == 0) {
                                            Modifier.background(
                                                primaryColor.copy(alpha = 0.15f),
                                                RoundedCornerShape(4.dp)
                                            )
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday || count > 0) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculate intensity using a logarithmic scale for better visualization
 */
private fun calculateIntensity(count: Int, maxCount: Int): Float {
    if (count == 0) return 0f
    if (maxCount <= 1) return 1f
    
    val logCount = kotlin.math.ln((count + 1).toDouble())
    val logMax = kotlin.math.ln((maxCount + 1).toDouble())
    
    return (logCount / logMax).toFloat().coerceIn(0.4f, 1f)
}

@Composable
private fun ActivityLegend() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val intensities = listOf(0f, 0.4f, 0.6f, 0.8f, 1f)
        for (intensity in intensities) {
            val color = if (intensity == 0f) surfaceVariant.copy(alpha = 0.5f) 
                        else lerp(surfaceVariant, primaryColor, intensity)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

private fun calculateActivityDataFromTimestamps(timestamps: List<String>): Map<LocalDate, Int> {
    val activityCounts = mutableMapOf<LocalDate, Int>()
    
    timestamps.forEach { timestamp ->
        try {
            // Try ISO format with timezone (e.g., 2025-02-07T08:46:19Z)
            val date = ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
                .toLocalDate()
            activityCounts[date] = (activityCounts[date] ?: 0) + 1
            Log.d("ActivityMapCard", "Parsed date: $date from $timestamp")
        } catch (e: DateTimeParseException) {
            try {
                // Try just date part
                val date = LocalDate.parse(timestamp.substring(0, 10))
                activityCounts[date] = (activityCounts[date] ?: 0) + 1
            } catch (e2: Exception) {
                Log.w("ActivityMapCard", "Failed to parse timestamp: $timestamp")
            }
        }
    }
    
    Log.d("ActivityMapCard", "Calculated activity data: $activityCounts")
    return activityCounts
}
