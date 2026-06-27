package com.example.suicanfcreader.view.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val summaries = topScreenViewModel.cardSummaries.observeAsState(emptyList())
    val selectedCardId = topScreenViewModel.selectedCardId.observeAsState()
    val selectedHistory = topScreenViewModel.selectedHistory.observeAsState(emptyList())
    val searchQuery = topScreenViewModel.searchQuery.observeAsState("")
    val accentColorHex = topScreenViewModel.accentColorHex.observeAsState("#8AD7C8")
    val balanceColorHex = topScreenViewModel.balanceColorHex.observeAsState("#8AD7C8")
    val summaryBackgroundColorHex = topScreenViewModel.summaryBackgroundColorHex.observeAsState("#103F3A")
    val noticeBackgroundColorHex = topScreenViewModel.noticeBackgroundColorHex.observeAsState("#174F47")
    val deleteButtonColorHex = topScreenViewModel.deleteButtonColorHex.observeAsState("#533232")
    val appTitle = topScreenViewModel.appTitle.observeAsState("SuicaNFC KD")
    val useSearchIcon = topScreenViewModel.useSearchIcon.observeAsState(true)
    val showLegacySearchBar = topScreenViewModel.showLegacySearchBar.observeAsState(false)
    val searchDialogVisible = topScreenViewModel.searchDialogVisible.observeAsState(false)
    val showCardBalances = topScreenViewModel.showCardBalances.observeAsState(true)
    val settingsDialogVisible = topScreenViewModel.settingsDialogVisible.observeAsState(false)
    val readCardIds = topScreenViewModel.readCardIds.observeAsState(emptySet())
    val featureFlags = topScreenViewModel.featureFlags.observeAsState(emptyMap())
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val selectedSummary = summaries.value.firstOrNull { it.cardId == selectedCardId.value }
    val balanceColor = balanceColorHex.value.toComposeColor() ?: MaterialTheme.colorScheme.primary
    val summaryBackgroundColor = summaryBackgroundColorHex.value.toComposeColor() ?: Color(0xFF103F3A)
    val noticeBackgroundColor = noticeBackgroundColorHex.value.toComposeColor() ?: Color(0xFF174F47)
    val deleteButtonColor = deleteButtonColorHex.value.toComposeColor() ?: Color(0xFF533232)
    val groupedHistory = selectedHistory.value
        .mapIndexed { index, card -> index to card }
        .groupBy { it.second.date.orEmpty() }
    var aliasDialogCard by remember { mutableStateOf<SuicaCardSummary?>(null) }
    var editingRecord by remember { mutableStateOf<TransitHistoryRecord?>(null) }
    var showStatsDialog by remember { mutableStateOf(false) }

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
                balanceColor = balanceColor,
                onSelectCard = topScreenViewModel::selectCard
            )
        }

        item {
            BalanceSummary(
                summary = selectedSummary,
                hasFreshData = selectedSummary?.cardId in readCardIds.value,
                balanceColor = balanceColor,
                summaryBackgroundColor = summaryBackgroundColor,
                noticeBackgroundColor = noticeBackgroundColor,
                deleteButtonColor = deleteButtonColor,
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
                onRename = { selectedSummary?.let { aliasDialogCard = it } },
                onClearSelected = topScreenViewModel::clearSelectedCardHistory,
                canCopy = selectedSummary != null,
                canClearSelected = selectedSummary != null
            )
        }

        if (showCardBalances.value && summaries.value.size > 1) {
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
                    isRead = summary.cardId in readCardIds.value,
                    balanceColor = balanceColor,
                    onClick = { topScreenViewModel.selectCard(summary.cardId) }
                )
            }
        }

        if (showLegacySearchBar.value) {
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchQuery.value,
                    onValueChange = topScreenViewModel::setSearchQuery,
                    label = { Text("履歴検索・フィルタ") },
                    singleLine = true
                )
            }
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
            groupedHistory.forEach { (date, records) ->
                item(key = "date-$date") {
                    DateHeader(date.toJapaneseDateLabel())
                }
                items(records, key = { (_, card) -> "${card.cardId}-${card.number}-${card.date}" }) { (index, card) ->
                    HistoryCard(
                        card = card,
                        index = index + 1,
                        isLatest = index == 0,
                        balanceColor = balanceColor,
                        onEdit = { editingRecord = card }
                    )
                }
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

    if (searchDialogVisible.value) {
        SearchDialog(
            query = searchQuery.value,
            onQueryChange = topScreenViewModel::setSearchQuery,
            onDismiss = topScreenViewModel::dismissSearchDialog,
            resultCount = selectedHistory.value.size
        )
    }

    if (settingsDialogVisible.value) {
        SettingsDialog(
            appTitle = appTitle.value,
            accentColorHex = accentColorHex.value,
            balanceColorHex = balanceColorHex.value,
            summaryBackgroundColorHex = summaryBackgroundColorHex.value,
            noticeBackgroundColorHex = noticeBackgroundColorHex.value,
            deleteButtonColorHex = deleteButtonColorHex.value,
            useSearchIcon = useSearchIcon.value,
            showLegacySearchBar = showLegacySearchBar.value,
            showCardBalances = showCardBalances.value,
            featureFlags = featureFlags.value,
            onDismiss = topScreenViewModel::dismissSettingsDialog,
            onAppTitleSave = topScreenViewModel::setAppTitle,
            onAccentSave = topScreenViewModel::setAccentColor,
            onBalanceColorSave = topScreenViewModel::setBalanceColor,
            onSummaryBackgroundColorSave = topScreenViewModel::setSummaryBackgroundColor,
            onNoticeBackgroundColorSave = topScreenViewModel::setNoticeBackgroundColor,
            onDeleteButtonColorSave = topScreenViewModel::setDeleteButtonColor,
            onUseSearchIconChanged = topScreenViewModel::setUseSearchIcon,
            onShowLegacySearchBarChanged = topScreenViewModel::setShowLegacySearchBar,
            onShowCardBalancesChanged = topScreenViewModel::setShowCardBalances,
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
    balanceColor: Color,
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
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else balanceColor,
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
    balanceColor: Color,
    summaryBackgroundColor: Color,
    noticeBackgroundColor: Color,
    deleteButtonColor: Color,
    onCopyJson: () -> Unit,
    onCopyCsv: () -> Unit,
    onCopyNotion: () -> Unit,
    onStats: () -> Unit,
    onRename: () -> Unit,
    onClearSelected: () -> Unit,
    canCopy: Boolean,
    canClearSelected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = summaryBackgroundColor),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = summary?.title ?: "suicanfc kd",
                            color = Color(0xFFDDF7EF),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            modifier = Modifier.size(34.dp),
                            enabled = canCopy,
                            onClick = onRename
                        ) {
                            PencilIcon(Color(0xFFDDF7EF))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = noticeBackgroundColor
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            text = if (hasFreshData) "このカードを読み取りました" else "交通系ICカードをかざしてください",
                            color = Color(0xFFE8FFF8),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                StatusDot(isActive = hasFreshData)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = summary?.latestRecord.balanceText(),
                    color = balanceColor,
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
                ExportMenuButton(
                    enabled = canCopy,
                    onCopyJson = onCopyJson,
                    onCopyCsv = onCopyCsv,
                    onCopyNotion = onCopyNotion
                )
                Button(onClick = onStats, enabled = canCopy, shape = RoundedCornerShape(8.dp)) {
                    Text("統計")
                }
            }
            Button(
                onClick = onClearSelected,
                enabled = canClearSelected,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = deleteButtonColor,
                    contentColor = Color(0xFFFFF2F2),
                    disabledContainerColor = deleteButtonColor.copy(alpha = 0.38f),
                    disabledContentColor = Color(0xFFFFF2F2).copy(alpha = 0.5f)
                )
            ) {
                Text("このカードを削除")
            }
        }
    }
}

@Composable
private fun CardBalanceRow(
    summary: SuicaCardSummary,
    selected: Boolean,
    isRead: Boolean,
    balanceColor: Color,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(isActive = isRead)
                Text(
                    text = summary.latestRecord.balanceText(),
                    color = balanceColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExportMenuButton(
    enabled: Boolean,
    onCopyJson: () -> Unit,
    onCopyCsv: () -> Unit,
    onCopyNotion: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Button(
        onClick = { expanded = true },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text("エクスポート")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("CSV") },
            onClick = {
                onCopyCsv()
                expanded = false
            }
        )
        DropdownMenuItem(
            text = { Text("JSON") },
            onClick = {
                onCopyJson()
                expanded = false
            }
        )
        DropdownMenuItem(
            text = { Text("Notion") },
            onClick = {
                onCopyNotion()
                expanded = false
            }
        )
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
private fun PencilIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.10f
        drawLine(
            color = color,
            start = Offset(size.width * 0.25f, size.height * 0.78f),
            end = Offset(size.width * 0.72f, size.height * 0.31f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.64f, size.height * 0.23f),
            end = Offset(size.width * 0.78f, size.height * 0.37f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.20f, size.height * 0.84f),
            end = Offset(size.width * 0.36f, size.height * 0.80f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun CashRegisterIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.08f)
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.18f, size.height * 0.42f),
            size = Size(size.width * 0.64f, size.height * 0.38f),
            style = stroke
        )
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.30f, size.height * 0.20f),
            size = Size(size.width * 0.40f, size.height * 0.20f),
            style = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, size.height * 0.62f),
            end = Offset(size.width * 0.72f, size.height * 0.62f),
            strokeWidth = size.minDimension * 0.07f
        )
        drawCircle(color, radius = size.minDimension * 0.04f, center = Offset(size.width * 0.36f, size.height * 0.52f))
        drawCircle(color, radius = size.minDimension * 0.04f, center = Offset(size.width * 0.50f, size.height * 0.52f))
        drawCircle(color, radius = size.minDimension * 0.04f, center = Offset(size.width * 0.64f, size.height * 0.52f))
    }
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
private fun DateHeader(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun HistoryCard(
    card: TransitHistoryRecord,
    index: Int,
    isLatest: Boolean,
    balanceColor: Color,
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
                            color = balanceColor,
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

                val hasInPlace = card.hasInPlace()
                val hasOutPlace = card.hasOutPlace()
                if (card.isSpecialActivity()) {
                    ActivityLine(card = card)
                } else if (card.isBusActivity()) {
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
                } else if (hasInPlace || hasOutPlace) {
                    if (hasInPlace) {
                        RouteLine(
                            label = "入",
                            company = card.inCompany,
                            line = card.inLine,
                            station = card.inStation
                        )
                    }
                    if (hasOutPlace) {
                        RouteLine(
                            label = "出",
                            company = card.outCompany,
                            line = card.outLine,
                            station = card.outStation
                        )
                    }
                } else {
                    ActivityLine(card = card)
                }
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
private fun ActivityLine(card: TransitHistoryRecord) {
    val label = card.activityLabel()
    val place = card.activityPlace()
    val title = if (place != null && (label == "チャージ" || label == "物販")) {
        "$label ($place)"
    } else {
        label
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = when (label) {
                "チャージ" -> Color(0xFFDDF7EF)
                "物販" -> Color(0xFFFFE8B7)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (label == "物販") {
                    CashRegisterIcon(Color(0xFF203238))
                } else {
                    Text(
                        text = card.activityBadge(),
                        color = Color(0xFF203238),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(card.activityCompany(), card.activityLine(), card.device ?: card.action ?: card.kind)
                    .mapNotNull { it.firstReadableOrNull() }
                    .distinct()
                    .joinToString(" / ")
                    .ifBlank { "交通系IC" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            val primary = station.firstReadableOrNull()
                ?: line.firstReadableOrNull()
                ?: company.firstReadableOrNull()
                ?: "未記録"
            val secondary = listOf(company, line)
                .mapNotNull { it.firstReadableOrNull() }
                .distinct()
                .joinToString(" / ")
            Text(
                text = primary,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondary.ifBlank { "交通系IC" },
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
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    resultCount: Int
) {
    var localQuery by remember(query) { mutableStateOf(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("履歴検索") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = localQuery,
                    onValueChange = {
                        localQuery = it
                        onQueryChange(it)
                    },
                    label = { Text("履歴検索・フィルタ") },
                    singleLine = true
                )
                Text(
                    text = if (localQuery.isBlank()) "検索語を入力してください" else "${resultCount}件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    localQuery = ""
                    onQueryChange("")
                }
            ) { Text("クリア") }
        }
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
    appTitle: String,
    accentColorHex: String,
    balanceColorHex: String,
    summaryBackgroundColorHex: String,
    noticeBackgroundColorHex: String,
    deleteButtonColorHex: String,
    useSearchIcon: Boolean,
    showLegacySearchBar: Boolean,
    showCardBalances: Boolean,
    featureFlags: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onAppTitleSave: (String) -> Unit,
    onAccentSave: (String) -> Unit,
    onBalanceColorSave: (String) -> Unit,
    onSummaryBackgroundColorSave: (String) -> Unit,
    onNoticeBackgroundColorSave: (String) -> Unit,
    onDeleteButtonColorSave: (String) -> Unit,
    onUseSearchIconChanged: (Boolean) -> Unit,
    onShowLegacySearchBarChanged: (Boolean) -> Unit,
    onShowCardBalancesChanged: (Boolean) -> Unit,
    onFeatureChanged: (String, Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (String) -> Boolean
) {
    var title by remember(appTitle) { mutableStateOf(appTitle) }
    var accent by remember(accentColorHex) { mutableStateOf(accentColorHex) }
    var balanceColor by remember(balanceColorHex) { mutableStateOf(balanceColorHex) }
    var summaryBackgroundColor by remember(summaryBackgroundColorHex) { mutableStateOf(summaryBackgroundColorHex) }
    var noticeBackgroundColor by remember(noticeBackgroundColorHex) { mutableStateOf(noticeBackgroundColorHex) }
    var deleteButtonColor by remember(deleteButtonColorHex) { mutableStateOf(deleteButtonColorHex) }
    var backupText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    EditField("アプリ名", title) { title = it }
                }
                item {
                    Button(
                        onClick = {
                            onAppTitleSave(title)
                            message = "アプリ名を保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("アプリ名を保存")
                    }
                }
                item {
                    EditField("アクセントカラー (#RRGGBB)", accent) { accent = it }
                }
                item {
                    Button(
                        onClick = {
                            onAccentSave(accent)
                            message = "アクセントカラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("アクセントカラーを保存")
                    }
                }
                item {
                    EditField("残高表示カラー (#RRGGBB)", balanceColor) { balanceColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onBalanceColorSave(balanceColor)
                            message = "残高表示カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("残高表示カラーを保存")
                    }
                }
                item {
                    EditField("カードヘッダー背景カラー (#RRGGBB)", summaryBackgroundColor) { summaryBackgroundColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onSummaryBackgroundColorSave(summaryBackgroundColor)
                            message = "カードヘッダー背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("カードヘッダー背景カラーを保存")
                    }
                }
                item {
                    EditField("読み取り案内背景カラー (#RRGGBB)", noticeBackgroundColor) { noticeBackgroundColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onNoticeBackgroundColorSave(noticeBackgroundColor)
                            message = "読み取り案内背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("読み取り案内背景カラーを保存")
                    }
                }
                item {
                    EditField("削除ボタン背景カラー (#RRGGBB)", deleteButtonColor) { deleteButtonColor = it }
                }
                item {
                    Button(
                        onClick = {
                            onDeleteButtonColorSave(deleteButtonColor)
                            message = "削除ボタン背景カラーを保存しました"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("削除ボタン背景カラーを保存")
                    }
                }
                item {
                    FeatureFlagRow(
                        label = "虫眼鏡アイコン検索を表示",
                        checked = useSearchIcon,
                        onCheckedChange = onUseSearchIconChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "従来の履歴検索・フィルタを表示",
                        checked = showLegacySearchBar,
                        onCheckedChange = onShowLegacySearchBarChanged
                    )
                }
                item {
                    FeatureFlagRow(
                        label = "カード別残高を表示",
                        checked = showCardBalances,
                        onCheckedChange = onShowCardBalancesChanged
                    )
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

private fun TransitHistoryRecord.hasInPlace(): Boolean =
    listOf(inCompany, inLine, inStation).any { !it.isNullOrBlank() && it != "-" }

private fun TransitHistoryRecord.hasOutPlace(): Boolean =
    listOf(outCompany, outLine, outStation).any { !it.isNullOrBlank() && it != "-" }

private fun TransitHistoryRecord.activityLabel(): String {
    val text = listOf(action, kind, device).joinToString(" ")
    return when {
        "チャージ" in text || (amount?.toIntOrNull() ?: 0) > 0 -> "チャージ"
        "物販" in text -> "物販"
        "バス" in text -> "バス"
        else -> action ?: kind ?: "利用"
    }
}

private fun TransitHistoryRecord.activityBadge(): String =
    when (activityLabel()) {
        "チャージ" -> "+"
        "物販" -> "POS"
        else -> "IC"
    }

private fun TransitHistoryRecord.isSpecialActivity(): Boolean {
    val text = listOf(action, kind).joinToString(" ")
    return "チャージ" in text || "物販" in text
}

private fun TransitHistoryRecord.isBusActivity(): Boolean {
    val text = listOf(action, kind, device).joinToString(" ")
    return "バス" in text
}

private fun TransitHistoryRecord.activityPlace(): String? =
    inStation.firstReadableOrNull()
        ?: outStation.firstReadableOrNull()
        ?: inLine.firstReadableOrNull()
        ?: outLine.firstReadableOrNull()
        ?: inCompany.firstReadableOrNull()
        ?: outCompany.firstReadableOrNull()

private fun TransitHistoryRecord.activityCompany(): String? =
    inCompany.firstReadableOrNull() ?: outCompany.firstReadableOrNull()

private fun TransitHistoryRecord.activityLine(): String? =
    inLine.firstReadableOrNull() ?: outLine.firstReadableOrNull()

private fun String?.firstReadableOrNull(): String? {
    return this?.takeIf { it.isNotBlank() && it != "-" && it != "未記録" }
}

private fun String.toJapaneseDateLabel(): String {
    val parts = split("/")
    if (parts.size != 3) return ifBlank { "日付なし" }
    val year = parts[0].toIntOrNull() ?: return this
    val month = parts[1].toIntOrNull() ?: return this
    val day = parts[2].toIntOrNull() ?: return this
    return String.format(Locale.JAPAN, "%d年%d月%d日", year, month, day)
}

private fun String.toComposeColor(): Color? {
    val normalized = trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(normalized)) return null
    return normalized.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
