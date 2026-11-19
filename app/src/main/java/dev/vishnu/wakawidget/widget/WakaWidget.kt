package dev.vishnu.wakawidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.vishnu.wakawidget.Day
import dev.vishnu.wakawidget.MainActivity
import dev.vishnu.wakawidget.Stats
import dev.vishnu.wakawidget.StatsCache
import dev.vishnu.wakawidget.formatDuration
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.time.format.TextStyle as DayStyle

class WakaWidget : GlanceAppWidget() {

    companion object {
        val STATS_KEY = stringPreferencesKey("stats_json")
        val ERROR_KEY = stringPreferencesKey("error")

        // Size buckets: the launcher picks the biggest one that fits the widget.
        val SMALL = DpSize(110.dp, 50.dp)    // today's total
        val MEDIUM = DpSize(220.dp, 130.dp)  // + top languages and project
        val LARGE = DpSize(220.dp, 240.dp)   // + 7-day chart
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cachedJson = StatsCache.loadJson(context)
        provideContent {
            val json = currentState(STATS_KEY) ?: cachedJson
            val error = currentState(ERROR_KEY)
            val stats = remember(json) { json?.let { Stats.fromJson(it) } }
            GlanceTheme {
                WidgetContent(stats, error)
            }
        }
    }
}

@Composable
private fun WidgetContent(stats: Stats?, error: String?) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (stats == null) {
            Text(
                text = error ?: "Loading…",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
        } else {
            Header(stats, error)
            if (size.height >= WakaWidget.MEDIUM.height && size.width >= WakaWidget.MEDIUM.width) {
                Spacer(GlanceModifier.height(6.dp))
                Breakdown(stats, rows = if (size.height >= WakaWidget.LARGE.height) 3 else 2)
            }
            if (size.height >= WakaWidget.LARGE.height) {
                Spacer(GlanceModifier.height(10.dp))
                WeekChart(stats)
            }
        }
    }
}

@Composable
private fun Header(stats: Stats, error: String?) {
    val updated = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(stats.updatedAt))
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = formatDuration(stats.todaySeconds),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = error ?: "Today, as of $updated",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
        Text(
            text = "↻",
            modifier = GlanceModifier.padding(6.dp).clickable(actionRunCallback<RefreshAction>()),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 20.sp),
        )
    }
}

@Composable
private fun Breakdown(stats: Stats, rows: Int) {
    // Own Column: Glance caps each Row/Column at 10 children, so these rows
    // must not be emitted straight into the widget's outer Column.
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        BreakdownRows(stats, rows)
    }
}

@Composable
private fun BreakdownRows(stats: Stats, rows: Int) {
    val langs = stats.languages.take(rows)
    if (langs.isEmpty()) {
        Text(
            text = "No coding yet today",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        return
    }
    val top = langs.first().seconds.coerceAtLeast(1.0)
    langs.forEach { lang ->
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = lang.name,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                text = formatDuration(lang.seconds),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        LinearProgressIndicator(
            progress = (lang.seconds / top).toFloat(),
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        Spacer(GlanceModifier.height(3.dp))
    }
    stats.projects.firstOrNull()?.let { project ->
        Text(
            text = "Top project: ${project.name} (${formatDuration(project.seconds)})",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun WeekChart(stats: Stats) {
    val days = stats.week.takeLast(7)
    if (days.isEmpty()) return
    val max = days.maxOf { it.seconds }.coerceAtLeast(60.0)
    val today = LocalDate.now().toString()
    val maxBar = 56

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = "Last 7 days: ${formatDuration(days.sumOf { it.seconds })}",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
        )
        Spacer(GlanceModifier.height(4.dp))
        DayBars(days, max, today, maxBar)
    }
}

@Composable
private fun DayBars(days: List<Day>, max: Double, today: String, maxBar: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height((maxBar + 18).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val barHeight = ((day.seconds / max) * maxBar).toInt().coerceAtLeast(2)
            val label = runCatching {
                LocalDate.parse(day.date).dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault())
            }.getOrDefault("")
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(14.dp)
                        .height(barHeight.dp)
                        .cornerRadius(4.dp)
                        .background(
                            if (day.date == today) GlanceTheme.colors.primary
                            else GlanceTheme.colors.secondaryContainer
                        ),
                ) {}
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = label,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
        }
    }
}
