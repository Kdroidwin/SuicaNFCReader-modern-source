package com.example.suicanfcreader.model

import android.content.Context
import com.example.suicanfcreader.lib.SuicaReader

data class Card(
    var cardId: String? = null,
    var date: String? = null,
    var number: String? = null,
    var payment: String? = null,
    var amount: String? = null,
    var kind: String? = null,
    var device: String? = null,
    var action: String? = null,
    var inLine: String? = null,
    var inStation: String? = null,
    var outLine: String? = null,
    var outStation: String? = null,
    var balance: String? = null,
    var inCompany: String? = null,
    var outCompany: String? = null,
    var memo: String? = null,
    var tags: String? = null,
    var manuallyEdited: Boolean = false
) {
    companion object {
        fun getCard(context: Context?, felica: SuicaReader): Card {
            val stationPair = if (felica.isStationRecord()) {
                context?.let {
                    Station.resolvePair(
                        context = it,
                        regionCode = felica.regionCode,
                        inLineCode = felica.inLine,
                        inStationCode = felica.inStation,
                        outLineCode = felica.outLine,
                        outStationCode = felica.outStation
                    )
                }
            } else {
                null
            }
            val inStationDetails = stationPair?.first
            val outStationDetails = stationPair?.second

            return Card().apply {
                date = "%04d/%02d/%02d".format(2000 + felica.year, felica.month, felica.day)
                number = felica.seqNo.toString()
                payment = ""
                kind = felica.kind
                device = felica.device
                action = felica.action
                inLine = inStationDetails?.lineName
                inStation = inStationDetails?.stationName
                inCompany = inStationDetails?.company
                outLine = outStationDetails?.lineName
                outStation = outStationDetails?.stationName
                outCompany = outStationDetails?.company
                balance = felica.remain.toString()
            }
        }
    }
}
