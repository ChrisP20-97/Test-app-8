package dev.holo.launcher.ui.home

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holo.launcher.data.AppEntry
import dev.holo.launcher.data.searchApps
import dev.holo.launcher.ui.LauncherModel
import dev.holo.launcher.ui.Tab
import dev.holo.launcher.ui.components.CircleButton
import dev.holo.launcher.ui.components.CountBadge
import dev.holo.launcher.ui.components.HeaderPill
import dev.holo.launcher.ui.components.HoloCard
import dev.holo.launcher.ui.components.SubPanel
import dev.holo.launcher.ui.pages.AppIconView
import dev.holo.launcher.ui.theme.HoloColors
import dev.holo.launcher.ui.theme.HoloIcons
import dev.holo.launcher.ui.theme.HoloType
import dev.holo.launcher.ui.theme.LocalHolo
import dev.holo.launcher.ui.theme.HoloMetrics
import dev.holo.launcher.ui.components.holoShape
import dev.holo.launcher.ui.components.scaledRadius
import androidx.compose.ui.graphics.lerp
import dev.holo.launcher.ui.util.openIntent

@Composable
fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    m: LauncherModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) onQuery(text)
    }
    HoloCard(modifier.height(44.dp), radius = if (HoloMetrics.cut) HoloMetrics.radius.dp else 22.dp, padding = 0.dp, depth = 0.8f) {
        Row(
            Modifier.fillMaxSize().padding(start = 15.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(HoloIcons.Search, null, tint = Color(0xFFC9D8E8), modifier = Modifier.size(16.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = HoloType.body.copy(fontSize = 15.sp, color = HoloColors.TextBright),
                cursorBrush = SolidColor(LocalHolo.current.holo),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val first = searchApps(m.apps, query).firstOrNull()
                    if (first != null) {
                        m.container.apps.launch(first)
                        onQuery("")
                        focus.clearFocus()
                    }
                }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Search apps", style = HoloType.body.copy(fontSize = 15.sp, color = Color(0xFF8392A4)))
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                CircleButton(HoloIcons.Close, "Clear search", size = 32.dp, iconSize = 12.dp) {
                    onQuery("")
                    focus.clearFocus()
                }
            } else {
                CircleButton(HoloIcons.Mic, "Voice search", size = 32.dp, iconSize = 14.dp) {
                    runCatching {
                        speech.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say an app name")
                        )
                    }.onFailure { openIntent(context, Intent(Intent.ACTION_VOICE_COMMAND)) }
                }
            }
        }
    }
}

@Composable
fun SearchResults(query: String, m: LauncherModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val results = remember(query, m.apps) { searchApps(m.apps, query).take(24) }
    HoloCard(modifier) {
        HeaderPill("QUERY · ${query.trim().uppercase()}", HoloIcons.Search) {
            Text(
                "${results.size} MATCH${if (results.size == 1) "" else "ES"}",
                style = HoloType.label.copy(color = HoloColors.TextMid),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(results, key = { it.key }) { app ->
                ResultRow(app, m) {
                    m.container.apps.launch(app)
                    onDone()
                }
            }
            item {
                SubPanel(Modifier.fillMaxWidth().height(44.dp), radius = 9.dp, onClick = {
                    val ok = openIntent(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
                    if (!ok) openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
                    onDone()
                }) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(HoloIcons.Globe, null, tint = LocalHolo.current.holo, modifier = Modifier.size(18.dp))
                        Text(
                            "Search the web for “${query.trim()}”",
                            style = HoloType.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(app: AppEntry, m: LauncherModel, onClick: () -> Unit) {
    SubPanel(Modifier.fillMaxWidth().height(48.dp), radius = 9.dp, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconView(app, m, Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = HoloType.body.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = HoloType.label.copy(letterSpacing = 0.04.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val tabIcons = mapOf(
    Tab.HOME to HoloIcons.Home,
    Tab.COMMS to HoloIcons.Comms,
    Tab.MEDIA to HoloIcons.Media,
    Tab.TOOLS to HoloIcons.Tools,
    Tab.APPS to HoloIcons.Apps,
)

@Composable
fun NavBar(tab: Tab, onTab: (Tab) -> Unit, badges: Map<Tab, Int>, modifier: Modifier = Modifier) {
    HoloCard(modifier.height(64.dp), radius = (HoloMetrics.radius * 1.25f).dp, padding = 5.dp, depth = 1f) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Tab.entries.forEach { t ->
                NavTab(t, t == tab, badges[t] ?: 0, Modifier.weight(1f).fillMaxHeight()) { onTab(t) }
            }
        }
    }
}

@Composable
private fun NavTab(tab: Tab, selected: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = holoShape(scaledRadius(11.dp))
    val bg = if (selected) {
        Brush.verticalGradient(
            listOf(
                lerp(HoloColors.PanelTop, HoloColors.Border, 0.35f).copy(alpha = 0.6f),
                lerp(HoloColors.PanelMid, HoloColors.Border, 0.18f).copy(alpha = 0.6f),
            )
        )
    } else {
        Brush.verticalGradient(listOf(HoloColors.PanelBottom.copy(alpha = 0.72f), HoloColors.PanelBottom.copy(alpha = 0.72f)))
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, HoloColors.Border.copy(alpha = if (selected) 0.6f else 0.16f), shape)
            .clickable(role = Role.Tab, onClick = onClick),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val color = if (selected) HoloColors.TextBright else HoloColors.TextMid
            Icon(tabIcons.getValue(tab), null, tint = color, modifier = Modifier.size(19.dp))
            if (LocalHolo.current.navLabels) Text(tab.label, style = HoloType.tab.copy(color = color), maxLines = 1)
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .fillMaxWidth(0.44f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HoloColors.Highlight)
            )
        }
        if (badge > 0) {
            CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 6.dp))
        }
    }
}
