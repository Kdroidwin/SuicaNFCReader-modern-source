package com.example.suicanfcreader.model

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

data class Station(
    val regionCode: Int = -1,
    var lineName: String = "",
    var stationName: String = "",
    var company: String = ""
) {
    companion object {
        @Volatile
        private var stationCache: StationCache? = null

        fun getStation(context: Context, regionCode: Int, lineCode: Int, stationCode: Int): Station? {
            return resolvePair(
                context = context,
                regionCode = regionCode,
                inLineCode = lineCode,
                inStationCode = stationCode,
                outLineCode = 0,
                outStationCode = 0
            ).first
        }

        fun resolvePair(
            context: Context,
            regionCode: Int,
            inLineCode: Int,
            inStationCode: Int,
            outLineCode: Int,
            outStationCode: Int
        ): Pair<Station?, Station?> {
            val cache = loadStations(context)
            val inCandidates = cache.candidates(inLineCode, inStationCode)
            val outCandidates = cache.candidates(outLineCode, outStationCode)
            val inExact = inCandidates.firstOrNull { it.regionCode == regionCode }
            val outExact = outCandidates.firstOrNull { it.regionCode == regionCode }

            val sharedRegion = inCandidates.map { it.regionCode }
                .intersect(outCandidates.map { it.regionCode }.toSet())
                .preferPrivateRailRegion(inLineCode, outLineCode)
            if (sharedRegion != null && (sharedRegion != 0 || isPrivateRailLike(inLineCode, outLineCode))) {
                return inCandidates.firstOrNull { it.regionCode == sharedRegion } to
                    outCandidates.firstOrNull { it.regionCode == sharedRegion }
            }

            if (inExact != null || outExact != null) {
                return inExact to outExact
            }

            return inCandidates.preferred() to outCandidates.preferred()
        }

        private fun loadStations(context: Context): StationCache {
            stationCache?.let { return it }
            return synchronized(this) {
                stationCache ?: readStationCsv(context).also { stationCache = it }
            }
        }

        private fun readStationCsv(context: Context): StationCache {
            val byLineStation = linkedMapOf<String, MutableList<Station>>()

            try {
                context.assets.open("StationCode.csv").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { br ->
                        br.lineSequence().forEach { line ->
                            val tokens = line.split(",").map { it.trim() }
                            if (tokens.size >= 6) {
                                val region = tokens[0].toIntOrNull() ?: return@forEach
                                val lineCode = tokens[1].toIntOrNull() ?: return@forEach
                                val stationCode = tokens[2].toIntOrNull() ?: return@forEach
                                if (lineCode == 0 && stationCode == 0) return@forEach

                                val station = Station(
                                    regionCode = region,
                                    company = tokens[3],
                                    lineName = tokens[4],
                                    stationName = normalizeStationName(tokens[3], tokens[5])
                                )
                                byLineStation
                                    .getOrPut(lineStationKey(lineCode, stationCode)) { mutableListOf() }
                                    .add(station)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return StationCache(byLineStation = byLineStation)
        }

        private fun StationCache.candidates(lineCode: Int, stationCode: Int): List<Station> {
            if (lineCode == 0 && stationCode == 0) return emptyList()
            return byLineStation[lineStationKey(lineCode, stationCode)].orEmpty()
        }

        private fun List<Station>.preferred(): Station? {
            if (isEmpty()) return null
            return firstOrNull { it.regionCode != 0 } ?: first()
        }

        private fun Set<Int>.preferPrivateRailRegion(inLineCode: Int, outLineCode: Int): Int? {
            if (isEmpty()) return null
            return if (isPrivateRailLike(inLineCode, outLineCode)) {
                firstOrNull { it != 0 } ?: first()
            } else {
                first()
            }
        }

        private fun isPrivateRailLike(vararg lineCodes: Int): Boolean =
            lineCodes.any { it >= 128 }

        private fun normalizeStationName(company: String, stationName: String): String {
            val hankyu = "\u962a\u6025\u96fb\u9244"
            val hanshin = "\u962a\u795e\u96fb\u6c17\u9244"
            val umeda = "\u6885\u7530"
            val osakaUmeda = "\u5927\u962a\u6885\u7530"
            val sannomiya = "\u4e09\u5bae"
            val kobeSannomiya = "\u795e\u6238\u4e09\u5bae"
            return when {
                (company == hankyu || company == hanshin) && stationName == umeda -> osakaUmeda
                (company == hankyu || company == hanshin) && stationName == sannomiya -> kobeSannomiya
                else -> stationName
            }
        }

        private fun lineStationKey(lineCode: Int, stationCode: Int): String =
            "$lineCode:$stationCode"

        private data class StationCache(
            val byLineStation: Map<String, List<Station>>
        )
    }
}
