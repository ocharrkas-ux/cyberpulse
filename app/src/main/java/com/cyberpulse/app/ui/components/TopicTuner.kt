package com.cyberpulse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyberpulse.app.domain.BriefingBuilder
import com.cyberpulse.app.domain.BriefingItem
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.Topic
import com.cyberpulse.app.domain.TopicKind
import com.cyberpulse.app.domain.TopicMatcher
import com.cyberpulse.app.domain.TopicRules
import com.cyberpulse.app.domain.WatchLevel
import com.cyberpulse.app.ui.theme.Fsociety

/** Something on a briefing card the user can flag or suppress. */
private sealed interface Tunable {
    val label: String

    data class ForTopic(val topic: Topic) : Tunable {
        override val label = "${topic.kind.label}: ${topic.display}"
    }

    data class ForSystem(val type: SystemType) : Tunable {
        override val label = "system: ${type.label}"
    }
}

private fun tunablesFor(item: BriefingItem): List<Tunable> = buildList {
    val product = item.product ?: if (!item.hasCoverage) BriefingBuilder.productOf(item.title) else null
    product?.let { add(Tunable.ForTopic(Topic(TopicKind.PRODUCT, it))) }
    TopicMatcher.suggestKeywords(item.title)
        .filterNot { product != null && product.contains(it, ignoreCase = true) }
        .forEach { add(Tunable.ForTopic(Topic(TopicKind.KEYWORD, it))) }
    item.systemTypes.forEach { add(Tunable.ForSystem(it)) }
    add(Tunable.ForTopic(Topic(TopicKind.CATEGORY, item.category.name)))
    item.sources.forEach { add(Tunable.ForTopic(Topic(TopicKind.SOURCE, it))) }
}

@Composable
fun TopicTunerDialog(
    item: BriefingItem,
    rules: TopicRules,
    watch: Map<SystemType, WatchLevel>,
    onSetTopic: (Topic, WatchLevel) -> Unit,
    onSetSystem: (SystemType, WatchLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    val tunables = remember(item) { tunablesFor(item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Fsociety.Panel,
        shape = RoundedCornerShape(4.dp),
        title = {
            Column {
                Text("// tune future briefings", style = MaterialTheme.typography.titleMedium, color = Fsociety.White)
                Text(
                    "flag = always include · suppress = leave out",
                    style = MaterialTheme.typography.labelSmall,
                    color = Fsociety.Grey,
                )
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tunables, key = { it.label }) { tunable ->
                    when (tunable) {
                        is Tunable.ForTopic -> TuneRow(tunable.label, rules.levelOf(tunable.topic)) {
                            onSetTopic(tunable.topic, it)
                        }
                        is Tunable.ForSystem -> TuneRow(tunable.label, watch[tunable.type] ?: WatchLevel.DEFAULT) {
                            onSetSystem(tunable.type, it)
                        }
                    }
                }
                item { CustomKeywordRow(onSetTopic) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("[done]", color = Fsociety.Green) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuneRow(label: String, level: WatchLevel, onChange: (WatchLevel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = when (level) {
                WatchLevel.FLAGGED -> Fsociety.Green
                WatchLevel.SUPPRESSED -> Fsociety.Grey
                WatchLevel.DEFAULT -> Fsociety.White
            },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WatchLevel.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = level == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, WatchLevel.entries.size, RoundedCornerShape(2.dp)),
                    icon = {},
                ) { Text(option.label.lowercase(), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
fun CustomKeywordRow(onSetTopic: (Topic, WatchLevel) -> Unit) {
    var text by remember { mutableStateOf("") }
    val keyword = text.trim()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("custom keyword", style = MaterialTheme.typography.bodySmall, color = Fsociety.White)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(60) },
            singleLine = true,
            placeholder = { Text("e.g. Fortinet, ransomware", color = Fsociety.Grey) },
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Fsociety.Green,
                unfocusedBorderColor = Fsociety.Line,
                cursorColor = Fsociety.Green,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                enabled = keyword.length >= 2,
                onClick = { onSetTopic(Topic(TopicKind.KEYWORD, keyword), WatchLevel.FLAGGED); text = "" },
            ) { Text("[flag]", color = if (keyword.length >= 2) Fsociety.Green else Fsociety.Grey) }
            TextButton(
                enabled = keyword.length >= 2,
                onClick = { onSetTopic(Topic(TopicKind.KEYWORD, keyword), WatchLevel.SUPPRESSED); text = "" },
            ) { Text("[suppress]", color = if (keyword.length >= 2) Fsociety.RedGlow else Fsociety.Grey) }
        }
    }
}

/** Compact list of saved topic rules with a way to clear each one. */
@Composable
fun TopicRuleList(rules: TopicRules, onSetTopic: (Topic, WatchLevel) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (rules.isEmpty) {
            Text(
                "> none yet. use [⋯] on a briefing card, or add a keyword below.",
                style = MaterialTheme.typography.bodySmall,
                color = Fsociety.Grey,
            )
        }
        rules.rules.forEach { rule ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rule.level == WatchLevel.FLAGGED) "[flag]" else "[mute]",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rule.level == WatchLevel.FLAGGED) Fsociety.Green else Fsociety.RedGlow,
                )
                Text(
                    " ${rule.topic.kind.label}: ${rule.topic.display}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fsociety.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSetTopic(rule.topic, WatchLevel.DEFAULT) }) {
                    Text("[x]", color = Fsociety.Grey)
                }
            }
        }
    }
}
