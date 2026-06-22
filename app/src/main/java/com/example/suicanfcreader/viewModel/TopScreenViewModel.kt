package com.example.suicanfcreader.viewModel

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suicanfcreader.lib.SuicaReader
import com.example.suicanfcreader.model.AppThemeMode
import com.example.suicanfcreader.model.Card
import com.example.suicanfcreader.model.SuicaCardSummary
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TopScreenViewModel(
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val initialHistory = loadHistory()
    private val initialSelectedCardId =
        preferences.getString(KEY_SELECTED_CARD_ID, null)
            ?: buildSummaries(initialHistory).firstOrNull()?.cardId

    private val _nfcData = MutableLiveData("")
    val nfcData: LiveData<String> get() = _nfcData

    private val _showNoNfcDialog = MutableLiveData(false)
    val showNoNfcDialog: LiveData<Boolean> = _showNoNfcDialog

    private val _isDataRefreshed = MutableLiveData(false)
    val isDataRefreshed: LiveData<Boolean> = _isDataRefreshed

    private val _latestCards = MutableLiveData<List<Card>>(emptyList())
    val latestCards: LiveData<List<Card>> = _latestCards

    private val _history = MutableLiveData(initialHistory)
    val history: LiveData<List<Card>> = _history

    private val _cardSummaries = MutableLiveData(buildSummaries(initialHistory))
    val cardSummaries: LiveData<List<SuicaCardSummary>> = _cardSummaries

    private val _selectedCardId = MutableLiveData(initialSelectedCardId)
    val selectedCardId: LiveData<String?> = _selectedCardId

    private val _selectedHistory =
        MutableLiveData(filterHistory(initialHistory, initialSelectedCardId, ""))
    val selectedHistory: LiveData<List<Card>> = _selectedHistory

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _themeMode =
        MutableLiveData(AppThemeMode.fromName(preferences.getString(KEY_THEME_MODE, null)))
    val themeMode: LiveData<AppThemeMode> = _themeMode

    private val _accentColorHex =
        MutableLiveData(preferences.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR)
    val accentColorHex: LiveData<String> = _accentColorHex

    private val _featureFlags = MutableLiveData(loadFeatureFlags())
    val featureFlags: LiveData<Map<String, Boolean>> = _featureFlags

    fun enableNfcForegroundDispatch(activity: Activity) {
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                val nfcIntentFilter = arrayOf(
                    IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                    IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                    IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
                )

                val pendingIntent =
                    PendingIntent.getActivity(
                        activity,
                        0,
                        Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_MUTABLE
                    )
                adapter.enableForegroundDispatch(activity, pendingIntent, nfcIntentFilter, null)
            } else {
                _showNoNfcDialog.postValue(true)
            }
        }
    }

    fun disableNfcForegroundDispatch(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    fun handleNfcIntent(intent: Intent?, context: Context) {
        intent?.let {
            if (intent.action in listOf(
                    NfcAdapter.ACTION_TAG_DISCOVERED,
                    NfcAdapter.ACTION_TECH_DISCOVERED,
                    NfcAdapter.ACTION_NDEF_DISCOVERED
                )
            ) {
                val tag = it.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                tag?.let {
                    viewModelScope.launch {
                        val data = readTagData(tag, context)
                        _nfcData.value = data.rawText
                        _latestCards.value = data.cards
                        if (data.cards.isNotEmpty()) {
                            val mergedHistory = mergeHistory(data.cards, loadHistory())
                            saveHistory(mergedHistory)
                            setSelectedCard(data.cardId)
                            refreshDerivedState(mergedHistory, data.cardId)
                        }
                        _isDataRefreshed.value = true
                    }
                }
            }
        }
    }

    fun selectCard(cardId: String) {
        setSelectedCard(cardId)
        refreshDerivedState(loadHistory(), cardId)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _selectedHistory.value = filterHistory(loadHistory(), _selectedCardId.value, query)
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccentColor(hex: String) {
        val normalized = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        if (!Regex("^#[0-9a-fA-F]{6}$").matches(normalized)) return
        preferences.edit().putString(KEY_ACCENT_COLOR, normalized).apply()
        _accentColorHex.value = normalized
    }

    fun setFeatureEnabled(key: String, enabled: Boolean) {
        val flags = loadFeatureFlags().toMutableMap()
        flags[key] = enabled
        val obj = JSONObject()
        flags.forEach { (flag, value) -> obj.put(flag, value) }
        preferences.edit().putString(KEY_FEATURE_FLAGS, obj.toString()).apply()
        _featureFlags.value = flags
    }

    fun setCardAlias(cardId: String, alias: String) {
        val aliases = loadAliases().toMutableMap()
        if (alias.isBlank()) {
            aliases.remove(cardId)
        } else {
            aliases[cardId] = alias.trim()
        }
        saveAliases(aliases)
        refreshDerivedState(loadHistory(), cardId)
    }

    fun updateRecord(originalNumber: String?, updated: Card) {
        val selectedCardId = _selectedCardId.value ?: return
        val updatedHistory = loadHistory().map { card ->
            if (card.resolvedCardId() == selectedCardId && card.number == originalNumber) {
                updated.copy(cardId = selectedCardId, manuallyEdited = true)
            } else {
                card
            }
        }
        saveHistory(updatedHistory)
        refreshDerivedState(updatedHistory, selectedCardId)
    }

    fun exportSelectedJson(): String {
        val array = JSONArray()
        filterHistory(loadHistory(), _selectedCardId.value, _searchQuery.value.orEmpty())
            .forEach { card -> array.put(card.toJson()) }
        return array.toString(2)
    }

    fun exportSelectedCsv(): String {
        val header = listOf(
            "date", "amount", "balance", "action", "device",
            "in_company", "in_line", "in_station", "out_company", "out_line", "out_station",
            "memo", "tags", "number"
        ).joinToString(",")
        val rows = filterHistory(loadHistory(), _selectedCardId.value, _searchQuery.value.orEmpty())
            .joinToString("\n") { card ->
                listOf(
                    card.date,
                    card.amount,
                    card.balance,
                    card.action ?: card.kind,
                    card.device,
                    card.inCompany,
                    card.inLine,
                    card.inStation,
                    card.outCompany,
                    card.outLine,
                    card.outStation,
                    card.memo,
                    card.tags,
                    card.number
                ).joinToString(",") { it.csvEscape() }
            }
        return "$header\n$rows"
    }

    fun exportSelectedNotionMarkdown(): String {
        val rows = filterHistory(loadHistory(), _selectedCardId.value, _searchQuery.value.orEmpty())
        val header = "| 日付 | 差額 | 残高 | タイトル | 場所 | メモ | タグ |\n|---|---:|---:|---|---|---|---|"
        val body = rows.joinToString("\n") { card ->
            val place = listOf(card.inStation, card.outStation).filterNot { it.isNullOrBlank() }.joinToString(" -> ")
            "| ${card.date.orEmpty()} | ${card.amount.orEmpty()} | ${card.balance.orEmpty()} | ${(card.action ?: card.kind).orEmpty()} | $place | ${card.memo.orEmpty()} | ${card.tags.orEmpty()} |"
        }
        return "$header\n$body"
    }

    fun statsText(): String {
        val rows = filterHistory(loadHistory(), _selectedCardId.value, "")
        val spending = rows.sumOf { card ->
            val amount = card.amount?.toIntOrNull() ?: 0
            if (amount < 0) -amount else 0
        }
        val charges = rows.sumOf { card ->
            val amount = card.amount?.toIntOrNull() ?: 0
            if (amount > 0) amount else 0
        }
        val movements = rows.count { !it.inStation.isNullOrBlank() || !it.outStation.isNullOrBlank() }
        val byMonth = rows.groupBy { it.date?.take(7).orEmpty() }
            .filterKeys { it.isNotBlank() }
            .toSortedMap(compareByDescending { it })
            .entries
            .take(12)
            .joinToString("\n") { (month, records) ->
                val monthSpending = records.sumOf { card ->
                    val amount = card.amount?.toIntOrNull() ?: 0
                    if (amount < 0) -amount else 0
                }
                "$month  支出 ${monthSpending}円 / 利用 ${records.size}件"
            }
        return buildString {
            appendLine("総支出: ${spending}円")
            appendLine("総チャージ: ${charges}円")
            appendLine("利用回数: ${rows.size}件")
            appendLine("移動回数: ${movements}回")
            if (byMonth.isNotBlank()) {
                appendLine()
                appendLine(byMonth)
            }
        }.trim()
    }

    fun exportBackupJson(): String {
        return JSONObject().apply {
            put("schema", 1)
            put("history", JSONArray(preferences.getString(KEY_HISTORY, "[]") ?: "[]"))
            put("cardAliases", JSONObject(preferences.getString(KEY_CARD_ALIASES, "{}") ?: "{}"))
            put("themeMode", _themeMode.value?.name ?: AppThemeMode.AMOLED.name)
            put("accentColor", _accentColorHex.value ?: DEFAULT_ACCENT_COLOR)
            put("features", JSONObject(preferences.getString(KEY_FEATURE_FLAGS, "{}") ?: "{}"))
        }.toString(2)
    }

    fun importBackupJson(rawJson: String): Boolean {
        return runCatching {
            val obj = JSONObject(rawJson)
            preferences.edit()
                .putString(KEY_HISTORY, obj.optJSONArray("history")?.toString() ?: "[]")
                .putString(KEY_CARD_ALIASES, obj.optJSONObject("cardAliases")?.toString() ?: "{}")
                .putString(KEY_THEME_MODE, obj.optString("themeMode", AppThemeMode.AMOLED.name))
                .putString(KEY_ACCENT_COLOR, obj.optString("accentColor", DEFAULT_ACCENT_COLOR))
                .putString(KEY_FEATURE_FLAGS, obj.optJSONObject("features")?.toString() ?: "{}")
                .apply()
            _themeMode.value = AppThemeMode.fromName(preferences.getString(KEY_THEME_MODE, null))
            _accentColorHex.value = preferences.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR
            _featureFlags.value = loadFeatureFlags()
            refreshDerivedState(loadHistory(), _selectedCardId.value)
        }.isSuccess
    }

    fun clearSelectedCardHistory() {
        val selectedCardId = _selectedCardId.value ?: return
        val remainingHistory = loadHistory().filterNot { it.resolvedCardId() == selectedCardId }
        saveHistory(remainingHistory)
        _latestCards.value = emptyList()
        _nfcData.value = ""
        _isDataRefreshed.value = false
        refreshDerivedState(remainingHistory, buildSummaries(remainingHistory).firstOrNull()?.cardId)
    }

    fun clearAllHistory() {
        preferences.edit()
            .remove(KEY_HISTORY)
            .remove(KEY_SELECTED_CARD_ID)
            .apply()
        _history.value = emptyList()
        _cardSummaries.value = emptyList()
        _selectedCardId.value = null
        _selectedHistory.value = emptyList()
        _latestCards.value = emptyList()
        _nfcData.value = ""
        _isDataRefreshed.value = false
    }

    private suspend fun readTagData(tag: Tag, context: Context): ReadResult =
        withContext(Dispatchers.IO) {
            val id = tag.id
            val cardId = id.toHexString()
            try {
                val felica = NfcF.get(tag)
                felica.connect()
<<<<<<< HEAD
                val readChunks = readHistoryChunks(felica, id, context, cardId)
                felica.close()
                val cards = withCalculatedAmounts(readChunks.cards)
                ReadResult(
                    cardId = cardId,
                    cards = cards,
                    rawText = buildRawText(cardId, cards, readChunks.rawResponses)
=======
                val res: ByteArray = felica.transceive(SuicaReader.readWithoutEncryption(id, READ_BLOCK_COUNT))
                felica.close()
                val cards = withCalculatedAmounts(fromData(res, context, cardId))
                ReadResult(
                    cardId = cardId,
                    cards = cards,
                    rawText = buildRawText(cardId, cards, res)
>>>>>>> origin/main
                )
            } catch (e: Exception) {
                ReadResult(
                    cardId = cardId,
                    cards = emptyList(),
                    rawText = "NFCタグの読み取りに失敗しました: ${e.message}"
                )
            }
        }

<<<<<<< HEAD
    private fun readHistoryChunks(
        felica: NfcF,
        id: ByteArray,
        context: Context,
        cardId: String
    ): ReadChunks {
        val cards = mutableListOf<Card>()
        val rawResponses = mutableListOf<ByteArray>()

        for (startBlock in 0 until MAX_READ_BLOCKS step READ_BLOCK_CHUNK_SIZE) {
            val requestSize = minOf(READ_BLOCK_CHUNK_SIZE, MAX_READ_BLOCKS - startBlock)
            val response = try {
                felica.transceive(SuicaReader.readWithoutEncryption(id, startBlock, requestSize))
            } catch (e: Exception) {
                if (startBlock == 0) throw e
                break
            }

            rawResponses.add(response)
            val chunkCards = fromData(response, context, cardId)
            if (chunkCards.isEmpty()) break

            cards.addAll(chunkCards)
            if (chunkCards.size < requestSize) break
        }

        return ReadChunks(
            cards = cards.distinctBy { listOf(it.number, it.date).joinToString("|") },
            rawResponses = rawResponses
        )
    }

=======
>>>>>>> origin/main
    private fun fromData(data: ByteArray, context: Context, cardId: String): List<Card> {
        if (data.size <= 13) return emptyList()

        val size: Int = data[12].toInt() and 0xff
        val cards = mutableListOf<Card>()
        for (i in 0 until size) {
            if (13 + i * 16 + 15 >= data.size) break
            val felica = SuicaReader.parse(data, 13 + i * 16)
<<<<<<< HEAD
            if (felica.year == 0 || felica.month !in 1..12 || felica.day !in 1..31) continue
=======
>>>>>>> origin/main
            val card: Card = Card.getCard(context, felica)
            card.cardId = cardId
            cards.add(card)
        }
        return cards
    }

    private fun withCalculatedAmounts(cards: List<Card>): List<Card> {
        return cards.mapIndexed { index, card ->
            val current = card.balance?.toIntOrNull()
            val previous = cards.getOrNull(index + 1)?.balance?.toIntOrNull()
            if (current != null && previous != null) {
                card.amount = (current - previous).toString()
            }
            card
        }
    }

<<<<<<< HEAD
    private fun buildRawText(cardId: String, cards: List<Card>, rawResponses: List<ByteArray>): String {
        val sb = StringBuilder()
        sb.appendLine("カードID: ${cardId.maskForDisplay()}")
        sb.appendLine("読み取り件数: ${cards.size}件")
=======
    private fun buildRawText(cardId: String, cards: List<Card>, data: ByteArray): String {
        val sb = StringBuilder()
        sb.appendLine("カードID: ${cardId.maskForDisplay()}")
>>>>>>> origin/main
        cards.forEachIndexed { index, card ->
            sb.appendLine("=== %02d ===".format(index + 1))
            sb.appendLine("端末種別: ${card.device.orEmpty()}")
            sb.appendLine("処理: ${card.action ?: card.kind ?: ""}")
            sb.appendLine("日付: ${card.date.orEmpty()}")
<<<<<<< HEAD
            val inPlace = listOf(card.inCompany, card.inLine, card.inStation).readableJoin().takeIf { it != "-" }
            val outPlace = listOf(card.outCompany, card.outLine, card.outStation).readableJoin().takeIf { it != "-" }
            if (inPlace != null || outPlace != null) {
                inPlace?.let { sb.appendLine("入場: $it") }
                outPlace?.let { sb.appendLine("出場: $it") }
            } else {
                sb.appendLine("種別: ${card.activityLabel()}")
            }
=======
            sb.appendLine("入場: ${listOf(card.inCompany, card.inLine, card.inStation).readableJoin()}")
            sb.appendLine("出場: ${listOf(card.outCompany, card.outLine, card.outStation).readableJoin()}")
>>>>>>> origin/main
            sb.appendLine("差額: ${card.amount.orEmpty()}")
            sb.appendLine("残高: ${card.balance.orEmpty()}")
            sb.appendLine()
        }
        sb.appendLine("BIN:")
<<<<<<< HEAD
        rawResponses.forEachIndexed { index, data ->
            sb.appendLine("[chunk ${index + 1}] ${data.joinToString(" ") { "%02x".format(it) }}")
        }
=======
        sb.appendLine(data.joinToString(" ") { "%02x".format(it) })
>>>>>>> origin/main
        return sb.toString().trim()
    }

    private fun mergeHistory(newCards: List<Card>, savedCards: List<Card>): List<Card> {
        val savedByRecord = savedCards.associateBy { it.recordKey() }
        val mergedNewCards = newCards.map { fresh ->
            savedByRecord[fresh.recordKey()]?.let { saved -> fresh.withUserEditsFrom(saved) } ?: fresh
        }
        return (mergedNewCards + savedCards)
            .distinctBy { it.recordKey() }
            .take(MAX_HISTORY_ITEMS)
    }

    private fun Card.recordKey(): String =
        listOf(resolvedCardId(), number ?: date.orEmpty()).joinToString("|")

    private fun Card.withUserEditsFrom(saved: Card): Card {
        val merged = copy(
            memo = saved.memo ?: memo,
            tags = saved.tags ?: tags
        )
        if (!saved.manuallyEdited) return merged
        return merged.copy(
            date = saved.date,
            amount = saved.amount,
<<<<<<< HEAD
            kind = saved.kind,
            device = saved.device,
=======
>>>>>>> origin/main
            action = saved.action,
            inCompany = saved.inCompany,
            inLine = saved.inLine,
            inStation = saved.inStation,
            outCompany = saved.outCompany,
            outLine = saved.outLine,
            outStation = saved.outStation,
            balance = saved.balance,
            manuallyEdited = true
        )
    }

    private fun refreshDerivedState(allCards: List<Card>, preferredCardId: String?) {
        val summaries = buildSummaries(allCards)
        val selectedCardId = preferredCardId
            ?.takeIf { id -> summaries.any { it.cardId == id } }
            ?: summaries.firstOrNull()?.cardId

        _history.value = allCards
        _cardSummaries.value = summaries
        _selectedCardId.value = selectedCardId
        _selectedHistory.value = filterHistory(allCards, selectedCardId, _searchQuery.value.orEmpty())
        setSelectedCard(selectedCardId)
    }

    private fun setSelectedCard(cardId: String?) {
        if (cardId == null) {
            preferences.edit().remove(KEY_SELECTED_CARD_ID).apply()
        } else {
            preferences.edit().putString(KEY_SELECTED_CARD_ID, cardId).apply()
        }
        _selectedCardId.value = cardId
    }

    private fun buildSummaries(cards: List<Card>): List<SuicaCardSummary> {
        val aliases = loadAliases()
        return cards
            .groupBy { it.resolvedCardId() }
            .map { (cardId, records) ->
                SuicaCardSummary(
                    cardId = cardId,
                    title = aliases[cardId] ?: cardId.displayTitle(records),
                    latestRecord = records.first(),
                    recordCount = records.size
                )
            }
            .sortedByDescending { it.latestRecord.number?.toIntOrNull() ?: 0 }
    }

    private fun filterHistory(cards: List<Card>, cardId: String?, query: String): List<Card> {
        val base = cardId?.let { id -> cards.filter { it.resolvedCardId() == id } } ?: emptyList()
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return base
        return base.filter { card ->
            listOf(
                card.date, card.amount, card.balance, card.kind, card.device, card.action,
                card.inCompany, card.inLine, card.inStation, card.outCompany, card.outLine,
                card.outStation, card.memo, card.tags
            ).any { it?.contains(normalizedQuery, ignoreCase = true) == true }
        }
    }

    private fun loadHistory(): List<Card> {
        val rawJson = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            List(array.length()) { index ->
                array.getJSONObject(index).toCard()
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHistory(cards: List<Card>) {
        val array = JSONArray()
        cards.forEach { card -> array.put(card.toJson()) }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun loadAliases(): Map<String, String> {
        val rawJson = preferences.getString(KEY_CARD_ALIASES, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(rawJson)
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrDefault(emptyMap())
    }

    private fun saveAliases(aliases: Map<String, String>) {
        val obj = JSONObject()
        aliases.forEach { (key, value) -> obj.put(key, value) }
        preferences.edit().putString(KEY_CARD_ALIASES, obj.toString()).apply()
    }

    private fun loadFeatureFlags(): Map<String, Boolean> {
        val defaults = linkedMapOf(
            "manual_edit" to true,
            "memo_tags" to true,
            "search_filter" to true,
            "statistics" to true,
            "exports" to true,
            "widget" to true,
            "card_alias" to true
        )
        val rawJson = preferences.getString(KEY_FEATURE_FLAGS, null) ?: return defaults
        return runCatching {
            val obj = JSONObject(rawJson)
            defaults.mapValues { (key, defaultValue) -> obj.optBoolean(key, defaultValue) }
        }.getOrDefault(defaults)
    }

    private fun Card.toJson(): JSONObject {
        return JSONObject().apply {
            putNullable("cardId", cardId)
            putNullable("date", date)
            putNullable("number", number)
            putNullable("payment", payment)
            putNullable("amount", amount)
            putNullable("kind", kind)
            putNullable("device", device)
            putNullable("action", action)
            putNullable("inLine", inLine)
            putNullable("inStation", inStation)
            putNullable("outLine", outLine)
            putNullable("outStation", outStation)
            putNullable("balance", balance)
            putNullable("inCompany", inCompany)
            putNullable("outCompany", outCompany)
            putNullable("memo", memo)
            putNullable("tags", tags)
            put("manuallyEdited", manuallyEdited)
        }
    }

    private fun JSONObject.toCard(): Card {
        return Card(
            cardId = nullableString("cardId"),
            date = nullableString("date"),
            number = nullableString("number"),
            payment = nullableString("payment"),
            amount = nullableString("amount"),
            kind = nullableString("kind"),
            device = nullableString("device"),
            action = nullableString("action"),
            inLine = nullableString("inLine"),
            inStation = nullableString("inStation"),
            outLine = nullableString("outLine"),
            outStation = nullableString("outStation"),
            balance = nullableString("balance"),
            inCompany = nullableString("inCompany"),
            outCompany = nullableString("outCompany"),
            memo = nullableString("memo"),
            tags = nullableString("tags"),
            manuallyEdited = optBoolean("manuallyEdited", false)
        )
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }

    private fun Card.resolvedCardId(): String = cardId ?: LEGACY_CARD_ID

    private fun String.displayTitle(records: List<Card>): String {
        return if (this == LEGACY_CARD_ID) {
            "保存済みカード"
        } else {
            "${records.inferTransitBrand()} ${maskForDisplay()}"
        }
    }

    private fun List<Card>.inferTransitBrand(): String {
        val text = flatMap { card ->
            listOf(card.inCompany, card.outCompany, card.inLine, card.outLine)
        }.joinToString(" ")
        return when {
            "JR西日本" in text || "Osaka Metro" in text -> "ICOCA/PiTaPa系"
            "JR東日本" in text -> "Suica/PASMO系"
            "JR東海" in text -> "TOICA/manaca系"
            "JR九州" in text -> "SUGOCA/nimoca系"
            else -> "交通系IC"
        }
    }

    private fun String.maskForDisplay(): String {
        return if (length <= 4) this else "****${takeLast(4)}"
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            String.format(Locale.US, "%02X", byte.toInt() and 0xff)
        }
    }

    private fun String?.csvEscape(): String {
        val raw = this.orEmpty()
        return "\"${raw.replace("\"", "\"\"")}\""
    }

    private fun List<String?>.readableJoin(): String =
        filterNot { it.isNullOrBlank() }.joinToString(" / ").ifBlank { "-" }

<<<<<<< HEAD
    private fun Card.activityLabel(): String {
        val text = listOf(action, kind, device).joinToString(" ")
        return when {
            "チャージ" in text || (amount?.toIntOrNull() ?: 0) > 0 -> "チャージ"
            "物販" in text -> "物販"
            "バス" in text -> "バス"
            else -> "利用"
        }
    }

=======
>>>>>>> origin/main
    private data class ReadResult(
        val cardId: String,
        val cards: List<Card>,
        val rawText: String
    )

<<<<<<< HEAD
    private data class ReadChunks(
        val cards: List<Card>,
        val rawResponses: List<ByteArray>
    )

=======
>>>>>>> origin/main
    companion object {
        private const val PREFS_NAME = "suica_reader_history"
        private const val KEY_HISTORY = "history"
        private const val KEY_SELECTED_CARD_ID = "selected_card_id"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CARD_ALIASES = "card_aliases"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_FEATURE_FLAGS = "feature_flags"
        private const val DEFAULT_ACCENT_COLOR = "#8AD7C8"
        private const val LEGACY_CARD_ID = "legacy"
<<<<<<< HEAD
        private const val READ_BLOCK_CHUNK_SIZE = 10
        private const val MAX_READ_BLOCKS = 50
=======
        private const val READ_BLOCK_COUNT = 10
>>>>>>> origin/main
        private const val MAX_HISTORY_ITEMS = 300
    }
}

class TopScreenViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TopScreenViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TopScreenViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
