package com.example.suicanfcreader.view.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.suicanfcreader.model.SuicaCardSummary
import com.example.suicanfcreader.viewModel.TopScreenViewModel
import java.util.Locale
import com.example.suicanfcreader.model.Card as TransitHistoryRecord

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopScreen(
    topScreenViewModel: TopScreenViewModel
) {
    val isDataRefreshed = topScreenViewModel.isDataRefreshed.observeAsState(false)
    val summaries = topScreenViewModel.cardSummaries.observeAsState(emptyList())
    val selectedCardId = topScreenViewModel.selectedCardId.observeAsState()
    val selectedHistory = topScreenViewModel.selectedHistory.observeAsState(emptyList())
    val searchQuery = topScreenViewModel.searchQuery.observeAsState("")
    val accentColorHex = topScreenViewModel.accentColorHex.observeAsState("#8AD7C8")
    val featureFlags = topScreenViewModel.featureFlags.observeAsState(emptyMap())
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val selectedSummary = summaries.value.firstOrNull { it.cardId == selectedCardId.value }
    var aliasDialogCard by remember { mutableStateOf<SuicaCardSummary?>(null) }
    var editingRecord by remember { mutableStateOf<TransitHistoryRecord?>(null) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            CardSelector(
                summaries = summaries.value,
                selectedCardId = selectedCardId.value,
                onSelectCard = topScreenViewModel::selectCard
            )
        }

        item {
            BalanceSummary(
                summary = selectedSummary,
                hasFreshData = isDataRefreshed.value && selectedSummary != null,
                onCopyJson = {
                    clipboardManager.setText(AnnotatedString(topScreenViewModel.exportSelectedJson()))
                },
                onCopyCsv = {
                    clipboardManager.setText(AnnotatedString(topScreenViewModel.exportSelectedCsv()))
                },
                onCopyNotion = {
                    clipboardManager.setText(AnnotatedString(topScreenViewModel.exportSelectedNotionMarkdown()))
                },
                onStats = { showStatsDialog = true },
                onSettings = { showSettingsDialog = true },
                onRename = { selectedSummary?.let { aliasDialogCard = it } },
                onClearSelected = topScreenViewModel::clearSelectedCardHistory,
                canCopy = selectedSummary != null,
                canClearSelected = selectedSummary != null
            )
        }

        if (summaries.value.size > 1) {
            item {
                SectionHeader(
                    title = "カード別残高",
                    supportingText = "${summaries.value.size}枚"
                )
            }
            items(summaries.value, key = { it.cardId }) { summary ->
                CardBalanceRow(
                    summary = summary,
                    selected = summary.cardId == selectedCardId.value,
                    onClick = { topScreenViewModel.selectCard(summary.cardId) }
                )
            }
        }

        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchQuery.value,
                onValueChange = topScreenViewModel::setSearchQuery,
                label = { Text("履歴検索・フィルタ") },
                singleLine = true
            )
        }

        item {
            SectionHeader(
                title = "選択中カードの履歴",
                supportingText = "${selectedHistory.value.size}件"
            )
        }

        if (selectedHistory.value.isEmpty()) {
            item {
                EmptyHistoryCard()
            }
        } else {
            itemsIndexed(selectedHistory.value) { index, card ->
                HistoryCard(
                    card = card,
                    index = index + 1,
                    isLatest = index == 0,
                    onEdit = { editingRecord = card }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    aliasDialogCard?.let { summary ->
        AliasDialog(
            summary = summary,
            onDismiss = { aliasDialogCard = null },
            onSave = { alias ->
                topScreenViewModel.setCardAlias(summary.cardId, alias)
                aliasDialogCard = null
            }
        )
    }

    editingRecord?.let { record ->
        EditHistoryDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { updated ->
                topScreenViewModel.updateRecord(record.number, updated)
                editingRecord = null
            }
        )
    }

    if (showStatsDialog) {
        TextDialog(
            title = "統計",
            text = topScreenViewModel.statsText(),
            onDismiss = { showStatsDialog = false },
            onCopy = { clipboardManager.setText(AnnotatedString(topScreenViewModel.statsText())) }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            accentColorHex = accentColorHex.value,
            featureFlags = featureFlags.value,
            onDismiss = { showSettingsDialog = false },
            onAccentSave = topScreenViewModel::setAccentColor,
            onFeatureChanged = topScreenViewModel::setFeatureEnabled,
            onExportBackup = {
                clipboardManager.setText(AnnotatedString(topScreenViewModel.exportBackupJson()))
            },
            onImportBackup = topScreenViewModel::importBackupJson
        )
    }
}

@Composable
private fun CardSelector(
    summaries: List<SuicaCardSummary>,
    selectedCardId: String?,
    onSelectCard: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "カード",
            supportingText = if (summaries.isEmpty()) "未登録" else "${summaries.size}枚"
        )
        if (summaries.isEmpty()) {
            Text(
                text = "交通系ICカードをかざすとカードごとに履歴と残高を保存します。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(summaries, key = { it.cardId }) { summary ->
                    val selected = summary.cardId == selectedCardId
                    Surface(
                        modifier = Modifier.clickable { onSelectCard(summary.cardId) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (selected) 0.dp else 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = summary.title,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = summary.latestRecord.balanceText(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceSummary(
    summary: SuicaCardSummary?,
    hasFreshData: Boolean,
    onCopyJson: () -> Unit,
    onCopyCsv: () -> Unit,
    onCopyNotion: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onRename: () -> Unit,
    onClearSelected: () -> Unit,
    canCopy: Boolean,
    canClearSelected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF103F3A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = summary?.title ?: "suicanfc kd",
                        color = Color(0xFFDDF7EF),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (hasFreshData) "このカードを読み取りました" else "交通系ICカードをかざしてください",
                        color = Color(0xFFA8D8CE),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                StatusDot(isActive = hasFreshData)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = summary?.latestRecord.balanceText() ?: "¥--",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary?.latestRecord?.date ?: "履歴はまだありません",
                    color = Color(0xFFC2E8DE),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onRename, enabled = canCopy, shape = RoundedCornerShape(8.dp)) {
                    Text("名前")
                }
                Button(onClick = onCopyJson, enabled = canCopy, shape = RoundedCornerShape(8.dp)) {
                    Text("JSON")
                }
                Button(onClick = onCopyCsv, enabled = canCopy, shape = RoundedCornerShape(8.dp)) {
                    Text("CSV")
                }
                Button(onClick = onCopyNotion, enabled = canCopy, shape = RoundedCornerShape(8.dp)) {
                    Text("Notion")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onStats, enabled = canCopy, shape = RoundedCornerShape(8.dp)) {
                    Text("統計")
                }
                Button(onClick = onSettings, shape = RoundedCornerShape(8.dp)) {
                    Text("設定")
                }
            }
            OutlinedButton(
                onClick = onClearSelected,
                enabled = canClearSelected,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("このカードを削除", color = Color(0xFFDDF7EF))
            }
        }
    }
}

@Composable
private fun CardBalanceRow(
    summary: SuicaCardSummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${summary.recordCount}件 / ${summary.latestRecord.date ?: "-"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = summary.latestRecord.balanceText(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusDot(isActive: Boolean) {
    val color = if (isActive) Color(0xFF9BE15D) else Color(0xFFFFC857)
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun SectionHeader(title: String, supportingText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = supportingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun HistoryCard(
    card: TransitHistoryRecord,
    index: Int,
    isLatest: Boolean,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLatest) 2.dp else 0.dp)
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = card.action ?: card.kind ?: "利用履歴",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = card.date ?: "-",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = card.amountText(),
                            color = card.amountColor(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = card.balanceText(),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        modifier = Modifier.weight(1f),
                        onClick = {},
                        label = {
                            Text(
                                text = card.device ?: "端末不明",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    AssistChip(
                        modifier = Modifier.weight(1f),
                        onClick = {},
                        label = {
                            Text(
                                text = "No.${card.number ?: index}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                RouteLine(
                    label = "入",
                    company = card.inCompany,
                    line = card.inLine,
                    station = card.inStation
                )
                RouteLine(
                    label = "出",
                    company = card.outCompany,
                    line = card.outLine,
                    station = card.outStation
                )
                if (!card.memo.isNullOrBlank() || !card.tags.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(card.memo, card.tags?.let { "#$it" }).joinToString("  "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onEdit) {
                    Text(if (card.manuallyEdited) "修正済み・再編集" else "手動修正")
                }
            }
        }
    }
}

@Composable
private fun RouteLine(label: String, company: String?, line: String?, station: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (label == "入") Color(0xFFDEEEF5) else Color(0xFFFFE4D6)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = Color(0xFF203238),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.takeReadable(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(company, line).map { it.takeReadable() }.joinToString(" / "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "このカードの履歴はまだありません",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "交通系ICカードをかざすとカード別に保存されます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AliasDialog(
    summary: SuicaCardSummary,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var alias by remember(summary.cardId) { mutableStateOf(summary.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("カード名を編集") },
        text = {
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("愛称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(alias) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun EditHistoryDialog(
    record: TransitHistoryRecord,
    onDismiss: () -> Unit,
    onSave: (TransitHistoryRecord) -> Unit
) {
    var title by remember(record.number) { mutableStateOf(record.action ?: record.kind.orEmpty()) }
    var date by remember(record.number) { mutableStateOf(record.date.orEmpty()) }
    var amount by remember(record.number) { mutableStateOf(record.amount.orEmpty()) }
    var balance by remember(record.number) { mutableStateOf(record.balance.orEmpty()) }
    var inCompany by remember(record.number) { mutableStateOf(record.inCompany.orEmpty()) }
    var inLine by remember(record.number) { mutableStateOf(record.inLine.orEmpty()) }
    var inStation by remember(record.number) { mutableStateOf(record.inStation.orEmpty()) }
    var outCompany by remember(record.number) { mutableStateOf(record.outCompany.orEmpty()) }
    var outLine by remember(record.number) { mutableStateOf(record.outLine.orEmpty()) }
    var outStation by remember(record.number) { mutableStateOf(record.outStation.orEmpty()) }
    var memo by remember(record.number) { mutableStateOf(record.memo.orEmpty()) }
    var tags by remember(record.number) { mutableStateOf(record.tags.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("履歴を手動修正") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { EditField("タイトル", title) { title = it } }
                item { EditField("日付", date) { date = it } }
                item { EditField("差額（+/-）", amount) { amount = it } }
                item { EditField("残高", balance) { balance = it } }
                item { EditField("入場会社・店舗種別", inCompany) { inCompany = it } }
                item { EditField("入場路線・分類", inLine) { inLine = it } }
                item { EditField("入場駅・店舗", inStation) { inStation = it } }
                item { EditField("出場会社・店舗種別", outCompany) { outCompany = it } }
                item { EditField("出場路線・分類", outLine) { outLine = it } }
                item { EditField("出場駅・店舗", outStation) { outStation = it } }
                item { EditField("メモ", memo) { memo = it } }
                item { EditField("タグ", tags) { tags = it } }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        record.copy(
                            action = title.ifBlank { record.action },
                            date = date.ifBlank { null },
                        amount = amount.ifBlank { null },
                        balance = balance.ifBlank { null },
                        inCompany = inCompany.ifBlank { null },
                        inLine = inLine.ifBlank { null },
                        inStation = inStation.ifBlank { null },
                        outCompany = outCompany.ifBlank { null },
                        outLine = outLine.ifBlank { null },
                        outStation = outStation.ifBlank { null },
                            memo = memo.ifBlank { null },
                            tags = tags.ifBlank { null },
                            manuallyEdited = true
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
private fun TextDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SelectionContainer {
                Text(text = text.ifBlank { "データがありません" })
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text("コピー") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
private fun SettingsDialog(
    accentColorHex: String,
    featureFlags: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onAccentSave: (String) -> Unit,
    onFeatureChanged: (String, Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (String) -> Boolean
) {
    var accent by remember(accentColorHex) { mutableStateOf(accentColorHex) }
    var backupText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    EditField("アクセント色 (#RRGGBB)", accent) { accent = it }
                }
                item {
                    Button(
                        onClick = {
                            onAccentSave(accent)
                            message = "色を保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("色を保存")
                    }
                }
                item {
                    Text(
                        text = "追加機能はデフォルトで有効です。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                items(featureFlags.entries.toList(), key = { it.key }) { entry ->
                    FeatureFlagRow(
                        label = entry.key.featureLabel(),
                        checked = entry.value,
                        onCheckedChange = { onFeatureChanged(entry.key, it) }
                    )
                }
                item {
                    Button(
                        onClick = {
                            onExportBackup()
                            message = "バックアップJSONをコピーしました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("バックアップをコピー")
                    }
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = backupText,
                        onValueChange = { backupText = it },
                        label = { Text("インポートJSON") },
                        minLines = 3
                    )
                }
                item {
                    Button(
                        onClick = {
                            message = if (onImportBackup(backupText)) {
                                "インポートしました"
                            } else {
                                "インポートに失敗しました"
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("インポート")
                    }
                }
                if (message.isNotBlank()) {
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
private fun FeatureFlagRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun String.featureLabel(): String =
    when (this) {
        "manual_edit" -> "手動修正"
        "memo_tags" -> "メモ・タグ"
        "search_filter" -> "履歴検索・フィルタ"
        "statistics" -> "統計"
        "exports" -> "CSV/JSON/Notionエクスポート"
        "widget" -> "ウィジェット"
        "card_alias" -> "カード名編集"
        else -> this
    }

private fun TransitHistoryRecord?.balanceText(): String {
    val balanceValue = this?.balance?.toIntOrNull()
    return if (balanceValue == null) {
        "¥--"
    } else {
        String.format(Locale.JAPAN, "¥%,d", balanceValue)
    }
}

private fun TransitHistoryRecord?.amountText(): String {
    val amountValue = this?.amount?.toIntOrNull()
    return if (amountValue == null) {
        "±--"
    } else {
        String.format(Locale.JAPAN, "%+,.0f", amountValue.toDouble())
    }
}

@Composable
private fun TransitHistoryRecord?.amountColor(): Color {
    val amountValue = this?.amount?.toIntOrNull() ?: return MaterialTheme.colorScheme.onSurfaceVariant
    return if (amountValue >= 0) Color(0xFF64D98A) else Color(0xFFFFB199)
}

private fun String?.takeReadable(): String {
    return this?.takeIf { it.isNotBlank() && it != "-" } ?: "未記録"
}
