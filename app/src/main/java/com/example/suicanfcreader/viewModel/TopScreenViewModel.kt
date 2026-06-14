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
        MutableLiveData(filterHistory(initialHistory, initialSelectedCardId))
    val selectedHistory: LiveData<List<Card>> = _selectedHistory

    private val _themeMode =
        MutableLiveData(AppThemeMode.fromName(preferences.getString(KEY_THEME_MODE, null)))
    val themeMode: LiveData<AppThemeMode> = _themeMode

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

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
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
                val req = SuicaReader.readWithoutEncryption(id, 10)
                val res: ByteArray = felica.transceive(req)
                felica.close()
                val cards = fromData(res, context, cardId)
                ReadResult(
                    cardId = cardId,
                    cards = cards,
                    rawText = buildRawText(cardId, cards, res)
                )
            } catch (e: Exception) {
                ReadResult(
                    cardId = cardId,
                    cards = emptyList(),
                    rawText = "NFCタグの読み取りに失敗しました: ${e.message}"
                )
            }
        }

    private fun fromData(data: ByteArray, context: Context, cardId: String): List<Card> {
        if (data.size <= 13) return emptyList()

        val size: Int = data[12].toInt() and 0xff
        val cards = mutableListOf<Card>()
        for (i in 0 until size) {
            if (13 + i * 16 + 15 >= data.size) break
            val felica = SuicaReader.parse(data, 13 + i * 16)
            val card: Card = Card.getCard(context, felica)
            card.cardId = cardId
            cards.add(card)
        }
        return cards
    }

    private fun buildRawText(cardId: String, cards: List<Card>, data: ByteArray): String {
        val sb = StringBuilder()
        sb.appendLine("カードID: ${cardId.maskForDisplay()}")
        cards.forEachIndexed { index, card ->
            sb.appendLine("=== %02d ===".format(index + 1))
            sb.appendLine("端末種: ${card.device.orEmpty()}")
            sb.appendLine("処理: ${card.action ?: card.kind ?: ""}")
            sb.appendLine("日付: ${card.date.orEmpty()}")
            sb.appendLine("入線区: ${card.inCompany.orEmpty()}-${card.inLine.orEmpty()}")
            sb.appendLine("入駅: ${card.inStation.orEmpty()}")
            sb.appendLine("出線区: ${card.outCompany.orEmpty()}-${card.outLine.orEmpty()}")
            sb.appendLine("出駅: ${card.outStation.orEmpty()}")
            sb.appendLine("残高: ${card.balance.orEmpty()}")
            sb.appendLine()
        }
        sb.appendLine("BIN:")
        sb.appendLine(data.joinToString(" ") { "%02x".format(it) })
        return sb.toString().trim()
    }

    private fun mergeHistory(newCards: List<Card>, savedCards: List<Card>): List<Card> {
        return (newCards + savedCards)
            .distinctBy { card ->
                listOf(
                    card.resolvedCardId(),
                    card.number,
                    card.date,
                    card.action,
                    card.kind,
                    card.balance,
                    card.inStation,
                    card.outStation
                ).joinToString("|")
            }
            .take(MAX_HISTORY_ITEMS)
    }

    private fun refreshDerivedState(allCards: List<Card>, preferredCardId: String?) {
        val summaries = buildSummaries(allCards)
        val selectedCardId = preferredCardId
            ?.takeIf { id -> summaries.any { it.cardId == id } }
            ?: summaries.firstOrNull()?.cardId

        _history.value = allCards
        _cardSummaries.value = summaries
        _selectedCardId.value = selectedCardId
        _selectedHistory.value = filterHistory(allCards, selectedCardId)
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
        return cards
            .groupBy { it.resolvedCardId() }
            .map { (cardId, records) ->
                SuicaCardSummary(
                    cardId = cardId,
                    title = cardId.displayTitle(),
                    latestRecord = records.first(),
                    recordCount = records.size
                )
            }
            .sortedByDescending { it.latestRecord.number?.toIntOrNull() ?: 0 }
    }

    private fun filterHistory(cards: List<Card>, cardId: String?): List<Card> {
        return cardId?.let { id -> cards.filter { it.resolvedCardId() == id } } ?: emptyList()
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

    private fun Card.toJson(): JSONObject {
        return JSONObject().apply {
            putNullable("cardId", cardId)
            putNullable("date", date)
            putNullable("number", number)
            putNullable("payment", payment)
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
        }
    }

    private fun JSONObject.toCard(): Card {
        return Card(
            cardId = nullableString("cardId"),
            date = nullableString("date"),
            number = nullableString("number"),
            payment = nullableString("payment"),
            kind = nullableString("kind"),
            device = nullableString("device"),
            action = nullableString("action"),
            inLine = nullableString("inLine"),
            inStation = nullableString("inStation"),
            outLine = nullableString("outLine"),
            outStation = nullableString("outStation"),
            balance = nullableString("balance"),
            inCompany = nullableString("inCompany"),
            outCompany = nullableString("outCompany")
        )
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }

    private fun Card.resolvedCardId(): String = cardId ?: LEGACY_CARD_ID

    private fun String.displayTitle(): String {
        return if (this == LEGACY_CARD_ID) {
            "保存済みカード"
        } else {
            "Suica ${maskForDisplay()}"
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

    private data class ReadResult(
        val cardId: String,
        val cards: List<Card>,
        val rawText: String
    )

    companion object {
        private const val PREFS_NAME = "suica_reader_history"
        private const val KEY_HISTORY = "history"
        private const val KEY_SELECTED_CARD_ID = "selected_card_id"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val LEGACY_CARD_ID = "legacy"
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
