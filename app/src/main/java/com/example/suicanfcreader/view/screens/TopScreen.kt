package com.example.suicanfcreader.view.screens

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
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
import com.example.suicanfcreader.model.Card as SuicaHistoryRecord

@Composable
fun TopScreen(
    topScreenViewModel: TopScreenViewModel
) {
    val nfcData = topScreenViewModel.nfcData.observeAsState("")
    val isDataRefreshed = topScreenViewModel.isDataRefreshed.observeAsState(false)
    val summaries = topScreenViewModel.cardSummaries.observeAsState(emptyList())
    val selectedCardId = topScreenViewModel.selectedCardId.observeAsState()
    val selectedHistory = topScreenViewModel.selectedHistory.observeAsState(emptyList())
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val selectedSummary = summaries.value.firstOrNull { it.cardId == selectedCardId.value }

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
                onCopy = {
                    if (nfcData.value.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(nfcData.value))
                    }
                },
                onClearSelected = topScreenViewModel::clearSelectedCardHistory,
                canCopy = nfcData.value.isNotBlank(),
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
                HistoryCard(card = card, index = index + 1, isLatest = index == 0)
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
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
                text = "Suicaをかざすとカードごとに履歴と残高を保存します",
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
    onCopy: () -> Unit,
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
                        text = summary?.title ?: "Suica Reader",
                        color = Color(0xFFDDF7EF),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (hasFreshData) "このカードを読み取りました" else "Suicaをかざしてください",
                        color = Color(0xFFA8D8CE),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                StatusDot(isActive = hasFreshData)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = summary?.latestRecord.balanceText(),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCopy,
                    enabled = canCopy,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("コピー")
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
private fun HistoryCard(card: SuicaHistoryRecord, index: Int, isLatest: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLatest) 2.dp else 0.dp)
    ) {
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
                Text(
                    text = card.balanceText(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
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
                text = "Suicaをかざすとカード別に保存されます",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun SuicaHistoryRecord?.balanceText(): String {
    val balanceValue = this?.balance?.toIntOrNull()
    return if (balanceValue == null) {
        "¥--"
    } else {
        String.format(Locale.JAPAN, "¥%,d", balanceValue)
    }
}

private fun String?.takeReadable(): String {
    return this?.takeIf { it.isNotBlank() && it != "-" } ?: "未記録"
}
