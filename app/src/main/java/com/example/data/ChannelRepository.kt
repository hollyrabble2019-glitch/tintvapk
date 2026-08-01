package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ChannelRepository(private val context: Context) {
    private val channelDao = AppDatabase.getDatabase(context).channelDao()

    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    val favorites: Flow<List<ChannelEntity>> = channelDao.getFavorites()

    suspend fun initializeIfNeeded() {
        withContext(Dispatchers.IO) {
            try {
                // Ensure we have something in database (first load / fallback)
                val count = channelDao.getAllChannels().first().size
                if (count == 0) {
                    loadChannelsFromAssets()
                }
            } catch (e: Exception) {
                Log.e("ChannelRepository", "Failed initial asset load check: ${e.message}")
            }

            // Always sync remote config from raw GitHub JSON repository
            syncFromGitHub()
        }
    }

    suspend fun syncFromGitHub(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("ChannelRepository", "[channels.json] JSON Download Started")
            // Download the JSON from raw GitHub repo URL with cache-busting timestamp
            val remoteUrl = "https://raw.githubusercontent.com/sakifooo/channels_data/refs/heads/main/channels.json?t=${System.currentTimeMillis()}"
            val result = loadChannelsFromRemoteUrl(remoteUrl)
            if (result.isSuccess) {
                val remoteChannels = result.getOrNull()?.toMutableList() ?: mutableListOf()
                
                // Also fetch and combine Live Matches from RemoteConfigManager
                val currentFavorites = channelDao.getFavorites().first()
                val favoriteIds = currentFavorites.map { it.id }.toSet()
                val favoriteNamesAndCountries = currentFavorites.map { it.name.lowercase() to it.country.lowercase() }.toSet()
                val liveMatchesList = convertLiveMatchesToEntities(RemoteConfigManager.liveMatches.value, favoriteIds, favoriteNamesAndCountries)
                remoteChannels.addAll(liveMatchesList)

                if (remoteChannels.isNotEmpty()) {
                    // Success: replace non-custom/standard channels with remote channels
                    channelDao.deleteNonCustomChannels()
                    channelDao.insertChannels(remoteChannels)
                    Log.d("ChannelRepository", "[channels.json] JSON Download Success")
                    Log.d("ChannelRepository", "[channels.json] Configuration Applied")
                    Log.d("ChannelRepository", "Successfully synced and updated database with ${remoteChannels.size} remote channels & matches from GitHub.")
                    Result.success(Unit)
                } else {
                    val errorMsg = "Downloaded remote channels list was empty"
                    Log.e("ChannelRepository", "[channels.json] Configuration Failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val exception = result.exceptionOrNull() ?: Exception("Unknown error downloading remote channels")
                Log.e("ChannelRepository", "[channels.json] Configuration Failed: ${exception.message}", exception)
                Result.failure(exception)
            }
        } catch (e: Exception) {
            Log.e("ChannelRepository", "[channels.json] Configuration Failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun convertLiveMatchesToEntities(liveMatchesConfig: LiveMatchesConfig, favoriteIds: Set<String>, favoriteNames: Set<Pair<String, String>>): List<ChannelEntity> {
        val list = mutableListOf<ChannelEntity>()
        if (!liveMatchesConfig.enabled || liveMatchesConfig.hidden) return list

        for (match in liveMatchesConfig.matches) {
            if (!match.enabled || match.hidden) continue
            val id = "live_match_${match.id.ifEmpty { match.order.toString() }}"
            val titleName = if (match.homeTeam.isNotBlank() && match.awayTeam.isNotBlank()) {
                "🔴 ${match.homeTeam} vs ${match.awayTeam}"
            } else if (match.competition.isNotBlank()) {
                "🔴 ${match.competition}"
            } else {
                "🔴 Match ${match.order}"
            }

            val streamsJsonString = if (match.servers.isNotEmpty()) {
                val arr = JSONArray()
                for (s in match.servers) {
                    if (s.enabled) {
                        val obj = org.json.JSONObject()
                        obj.put("name", s.name)
                        obj.put("player", s.type)
                        obj.put("url", s.url)
                        obj.put("priority", s.priority)
                        arr.put(obj)
                    }
                }
                arr.toString()
            } else null

            val primaryUrl = match.servers.firstOrNull { it.enabled }?.url ?: ""
            val logo = match.competitionLogo.ifBlank { match.homeLogo.ifBlank { match.thumbnail } }
            val isFav = favoriteIds.contains(id) || favoriteNames.contains(titleName.lowercase() to "live matches")

            list.add(
                ChannelEntity(
                    id = id,
                    name = titleName,
                    logo = logo,
                    country = "Live Matches",
                    category = "Live Matches",
                    streamUrl = primaryUrl,
                    isFavorite = isFav,
                    isCustom = false,
                    streamsJson = streamsJsonString
                )
            )
        }
        return list
    }

    private suspend fun loadChannelsFromRemoteUrl(urlString: String): Result<List<ChannelEntity>> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val jsonString = reader.use { it.readText() }
            
            Log.d("ChannelRepository", "Channels JSON Download Success")

            // Get current favorites to preserve favorite status
            val currentFavorites = channelDao.getFavorites().first()
            val favoriteIds = currentFavorites.map { it.id }.toSet()
            val favoriteNamesAndCountries = currentFavorites.map { it.name.lowercase() to it.country.lowercase() }.toSet()

            val channels = parseChannelsJsonString(jsonString, favoriteIds, favoriteNamesAndCountries)
            Result.success(channels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseChannelsJsonString(
        jsonString: String,
        favoriteIds: Set<String>,
        favoriteNamesAndCountries: Set<Pair<String, String>>
    ): List<ChannelEntity> {
        val channels = mutableListOf<ChannelEntity>()
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return channels

        try {
            if (trimmed.startsWith("{")) {
                val rootObj = JSONObject(trimmed)
                if (rootObj.has("channels")) {
                    val arr = rootObj.getJSONArray("channels")
                    parseArrayOrCountryObjects(arr, channels, favoriteIds, favoriteNamesAndCountries)
                } else if (rootObj.has("countries")) {
                    val arr = rootObj.getJSONArray("countries")
                    parseArrayOrCountryObjects(arr, channels, favoriteIds, favoriteNamesAndCountries)
                }
            } else if (trimmed.startsWith("[")) {
                val jsonArray = JSONArray(trimmed)
                parseArrayOrCountryObjects(jsonArray, channels, favoriteIds, favoriteNamesAndCountries)
            }
        } catch (e: Exception) {
            Log.e("ChannelRepository", "Error parsing channels JSON: ${e.message}", e)
        }

        return channels
    }

    private fun parseArrayOrCountryObjects(
        jsonArray: JSONArray,
        channels: MutableList<ChannelEntity>,
        favoriteIds: Set<String>,
        favoriteNamesAndCountries: Set<Pair<String, String>>
    ) {
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            if (item.has("channels") && item.get("channels") is JSONArray) {
                // Country or Category object containing a list of channels
                val countryName = item.optString("country", item.optString("name", item.optString("category", "General")))
                val countryLogo = item.optString("logo", item.optString("flag", ""))
                val chArr = item.getJSONArray("channels")
                for (j in 0 until chArr.length()) {
                    val chObj = chArr.optJSONObject(j) ?: continue
                    val entity = parseSingleChannelObj(chObj, countryName, countryLogo, favoriteIds, favoriteNamesAndCountries)
                    if (entity != null) channels.add(entity)
                }
            } else {
                // Flat channel object
                val entity = parseSingleChannelObj(item, item.optString("country", "General"), item.optString("country_logo", ""), favoriteIds, favoriteNamesAndCountries)
                if (entity != null) channels.add(entity)
            }
        }
    }

    private fun parseSingleChannelObj(
        chObj: JSONObject,
        defaultCountry: String,
        defaultCountryLogo: String,
        favoriteIds: Set<String>,
        favoriteNamesAndCountries: Set<Pair<String, String>>
    ): ChannelEntity? {
        val name = chObj.optString("name", chObj.optString("title", ""))
        if (name.isBlank()) return null

        val countryName = chObj.optString("country", defaultCountry).ifBlank { "General" }
        val streamsJsonString = if (chObj.has("streams")) {
            chObj.getJSONArray("streams").toString()
        } else {
            null
        }

        val streamUrl = if (countryName.equals("beIN SPORTS", ignoreCase = true) && chObj.has("stream_id")) {
            "bein_id:${chObj.get("stream_id")}"
        } else if (chObj.has("url") && chObj.optString("url", "").isNotBlank()) {
            chObj.optString("url", "")
        } else if (chObj.has("stream_url") && chObj.optString("stream_url", "").isNotBlank()) {
            chObj.optString("stream_url", "")
        } else if (chObj.has("stream_id")) {
            "bein_id:${chObj.get("stream_id")}"
        } else if (streamsJsonString != null) {
            try {
                val arr = JSONArray(streamsJsonString)
                if (arr.length() > 0) arr.getJSONObject(0).optString("url", "") else ""
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }

        val category = chObj.optString("category", 
            if (countryName.equals("beIN SPORTS", ignoreCase = true)) "beIN SPORTS" 
            else if (countryName.equals("Arabic News", ignoreCase = true)) "Arabic News"
            else if (countryName.equals("Documentaries", ignoreCase = true)) "Documentaries"
            else "General"
        )

        val rawLogo = chObj.optString("logo", chObj.optString("icon", chObj.optString("image", defaultCountryLogo)))
        val logo = if (rawLogo.isNotBlank()) {
            rawLogo
        } else if (countryName.equals("beIN SPORTS", ignoreCase = true) || category.equals("beIN SPORTS", ignoreCase = true) || name.lowercase().contains("bein")) {
            "http://kinglogo.newtvhd.com/logoo/BEIN4K/HD/bein%20sports%20HD.png"
        } else if (countryName.equals("THMANYAH", ignoreCase = true) || name.lowercase().contains("thmanyah")) {
            "https://www.wzufa.com/wp-content/uploads/2025/07/Thmanyah.png"
        } else if (countryName.equals("ALWAN SPORT", ignoreCase = true) || name.lowercase().contains("alwan")) {
            "http://kinglogo.newtvhd.com/logoo/AL%20MAJD/ALWAN.png"
        } else {
            ""
        }

        val rawId = chObj.optString("id", "")
        val id = if (rawId.isNotBlank()) rawId else "asset_${countryName.lowercase().filter { it.isLetterOrDigit() }}_${name.lowercase().filter { it.isLetterOrDigit() }}"

        val favPair = Pair(name.lowercase(), countryName.lowercase())
        val isFavorite = favoriteIds.contains(id) || favoriteNamesAndCountries.contains(favPair)

        return ChannelEntity(
            id = id,
            name = name,
            logo = logo,
            country = countryName,
            category = category,
            streamUrl = streamUrl,
            isFavorite = isFavorite,
            isCustom = false,
            streamsJson = streamsJsonString
        )
    }

    private suspend fun loadChannelsFromAssets() {
        try {
            val currentFavorites = channelDao.getFavorites().first()
            val favoriteIds = currentFavorites.map { it.id }.toSet()
            val favoriteNamesAndCountries = currentFavorites.map { it.name.lowercase() to it.country.lowercase() }.toSet()

            val jsonString = context.assets.open("channels.json").bufferedReader().use { it.readText() }
            val channels = parseChannelsJsonString(jsonString, favoriteIds, favoriteNamesAndCountries)
            
            if (channels.isNotEmpty()) {
                channelDao.insertChannels(channels)
                Log.d("ChannelRepository", "Loaded ${channels.size} channels from assets.")
            }
        } catch (e: Exception) {
            Log.e("ChannelRepository", "Error loading default channels: ${e.message}", e)
        }
    }

    suspend fun toggleFavorite(channelId: String, isFavorite: Boolean) {
        channelDao.updateFavoriteStatus(channelId, isFavorite)
    }

    suspend fun clearCache() {
        channelDao.clearAll()
        loadChannelsFromAssets()
    }

    suspend fun addChannel(channel: ChannelEntity) {
        channelDao.insertChannel(channel)
    }

    suspend fun deleteChannel(channel: ChannelEntity) {
        channelDao.deleteChannel(channel)
    }

    suspend fun importFromUrl(urlString: String, format: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val content = reader.use { it.readText() }

            if (format.lowercase() == "m3u" || content.trim().startsWith("#EXTM3U")) {
                importM3UContent(content)
            } else {
                importJSONContent(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importM3UContent(m3uContent: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val lines = m3uContent.split("\n")
            val channels = mutableListOf<ChannelEntity>()
            var currentChannelName = ""
            var currentLogo = ""
            var currentCountry = "Imported"
            var currentCategory = "General"

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    // Extract channel info
                    // Parse tvg-logo
                    currentLogo = extractAttribute(trimmed, "tvg-logo") ?: extractAttribute(trimmed, "logo") ?: ""
                    
                    // Parse group-title (often country/category)
                    currentCountry = extractAttribute(trimmed, "group-title") ?: extractAttribute(trimmed, "country") ?: "Imported"
                    currentCategory = extractAttribute(trimmed, "category") ?: "General"
                    
                    // The channel name is at the end of the EXTINF line, after the last comma
                    val commaIndex = trimmed.lastIndexOf(",")
                    currentChannelName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                        trimmed.substring(commaIndex + 1).trim()
                    } else {
                        "Unknown IPTV Channel"
                    }
                } else if (!trimmed.startsWith("#")) {
                    // This line contains the stream URL
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        val id = "imported_" + UUID.randomUUID().toString().take(8)
                        val name = currentChannelName.ifEmpty { "Channel " + id.takeLast(4) }
                        
                        channels.add(
                            ChannelEntity(
                                id = id,
                                name = name,
                                logo = currentLogo,
                                country = currentCountry,
                                category = currentCategory,
                                streamUrl = trimmed,
                                isFavorite = false,
                                isCustom = true
                            )
                        )
                        // Reset transient attributes
                        currentChannelName = ""
                        currentLogo = ""
                        currentCountry = "Imported"
                        currentCategory = "General"
                    }
                }
            }

            if (channels.isNotEmpty()) {
                channelDao.insertChannels(channels)
                Result.success(channels.size)
            } else {
                Result.failure(Exception("No valid M3U8 links found in the input"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractAttribute(line: String, attributeName: String): String? {
        val searchKey = "$attributeName=\""
        val startIndex = line.indexOf(searchKey)
        if (startIndex != -1) {
            val valueStart = startIndex + searchKey.length
            val endIndex = line.indexOf("\"", valueStart)
            if (endIndex != -1) {
                return line.substring(valueStart, endIndex)
            }
        }
        return null
    }

    suspend fun importJSONContent(jsonContent: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(jsonContent)
            val channels = mutableListOf<ChannelEntity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "imported_" + UUID.randomUUID().toString().take(8))
                channels.add(
                    ChannelEntity(
                        id = id,
                        name = obj.getString("name"),
                        logo = obj.optString("logo", ""),
                        country = obj.optString("country", "Imported"),
                        category = obj.optString("category", "General"),
                        streamUrl = obj.getString("stream_url"),
                        isFavorite = false,
                        isCustom = true
                    )
                )
            }
            if (channels.isNotEmpty()) {
                channelDao.insertChannels(channels)
                Result.success(channels.size)
            } else {
                Result.failure(Exception("No valid channels found in the JSON"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
