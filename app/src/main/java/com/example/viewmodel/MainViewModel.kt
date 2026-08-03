package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChannelEntity
import com.example.data.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object LiveMatches : Screen()
    object Countries : Screen()
    object Favorites : Screen()
    object Search : Screen()
    object Settings : Screen()
    object BeinSports : Screen()
    data class CountryDetail(val countryName: String) : Screen()
    data class CategoryDetail(val categoryName: String) : Screen()
    data class Player(val channel: ChannelEntity, val channelList: List<ChannelEntity> = emptyList()) : Screen()
}

data class CountryItem(
    val name: String,
    val flag: String,
    val channelCount: Int
)

data class AppState(
    val currentScreen: Screen = Screen.Home,
    val backstack: List<Screen> = listOf(Screen.Home),
    val allChannels: List<ChannelEntity> = emptyList(),
    val favorites: List<ChannelEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val importMessage: String? = null,
    val selectedChannel: ChannelEntity? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChannelRepository(application)
    
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.initializeIfNeeded()
            
            // Collect all channels
            launch {
                repository.allChannels.collect { channels ->
                    val processedChannels = channels.map { channel ->
                        if ((channel.country.equals("beIN SPORTS", ignoreCase = true) ||
                            channel.category.equals("beIN SPORTS", ignoreCase = true) ||
                            channel.name.lowercase().contains("bein")) && channel.logo.isNullOrBlank()
                        ) {
                            channel.copy(logo = "http://kinglogo.newtvhd.com/logoo/BEIN4K/HD/bein%20sports%20HD.png")
                        } else {
                            channel
                        }
                    }
                    _state.update { it.copy(allChannels = processedChannels, isLoading = false) }
                }
            }

            // Collect favorites
            launch {
                repository.favorites.collect { favs ->
                    val processedFavs = favs.map { channel ->
                        if ((channel.country.equals("beIN SPORTS", ignoreCase = true) ||
                            channel.category.equals("beIN SPORTS", ignoreCase = true) ||
                            channel.name.lowercase().contains("bein")) && channel.logo.isNullOrBlank()
                        ) {
                            channel.copy(logo = "http://kinglogo.newtvhd.com/logoo/BEIN4K/HD/bein%20sports%20HD.png")
                        } else {
                            channel
                        }
                    }
                    _state.update { it.copy(favorites = processedFavs) }
                }
            }

            // Dynamically resync DB when remote live matches update from GitHub
            launch {
                com.example.data.RemoteConfigManager.liveMatches.drop(1).collect {
                    repository.syncFromGitHub()
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _state.update { currentState ->
            val newBackstack = currentState.backstack.toMutableList()
            if (newBackstack.lastOrNull() != screen) {
                newBackstack.add(screen)
            }
            currentState.copy(
                currentScreen = screen,
                backstack = newBackstack
            )
        }
    }

    fun navigateBack(): Boolean {
        var handled = false
        var leftPlayer = false
        _state.update { currentState ->
            if (currentState.currentScreen is Screen.Player) {
                leftPlayer = true
            }
            val newBackstack = currentState.backstack.toMutableList()
            if (newBackstack.size > 1) {
                newBackstack.removeAt(newBackstack.size - 1)
                val prevScreen = newBackstack.last()
                handled = true
                currentState.copy(
                    currentScreen = prevScreen,
                    backstack = newBackstack
                )
            } else {
                currentState
            }
        }
        if (leftPlayer) {
            com.example.MediationAdManager.isPlayerScreenActive = false
            // Show interstitial ad if ready when returning from live stream player screen
            com.example.MediationAdManager.currentActivity?.let { activity ->
                com.example.MediationAdManager.showInterstitialIfReady(activity)
            }
        }
        return handled
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.id, !channel.isFavorite)
        }
    }

    fun selectChannel(channel: ChannelEntity) {
        val currentScreen = _state.value.currentScreen
        val playlist = when (currentScreen) {
            is Screen.CategoryDetail -> getChannelsForCategory(currentScreen.categoryName)
            is Screen.CountryDetail -> {
                _state.value.allChannels.filter { it.country.equals(currentScreen.countryName, ignoreCase = true) }
            }
            is Screen.BeinSports -> getBeInSportsChannels()
            is Screen.LiveMatches -> getLiveMatches()
            is Screen.Favorites -> _state.value.favorites
            else -> {
                getChannelsForCategory(channel.category)
            }
        }
        var finalPlaylist = playlist
        if (!finalPlaylist.any { it.id == channel.id }) {
            finalPlaylist = finalPlaylist + channel
        }
        _state.update { it.copy(selectedChannel = channel) }
        com.example.MediationAdManager.isPlayerScreenActive = true
        navigateTo(Screen.Player(channel, finalPlaylist))
    }

    fun syncFromGitHub(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.syncFromGitHub()
            _state.update { it.copy(isLoading = false) }
            result.onSuccess {
                showImportMessage("Channels successfully reloaded from GitHub!")
                onResult(true, "Channels successfully reloaded from GitHub!")
            }
            result.onFailure { error ->
                showImportMessage("Failed to reload channels: ${error.message}")
                onResult(false, "Failed to reload channels: ${error.message}")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.clearCache()
            showImportMessage("Cache cleared. Re-loaded default channels.")
        }
    }

    fun importM3U(content: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.importM3UContent(content)
            _state.update { it.copy(isLoading = false) }
            result.onSuccess { count ->
                showImportMessage("Successfully imported $count channels from M3U!")
            }
            result.onFailure { error ->
                showImportMessage("Failed to import M3U: ${error.message}")
            }
        }
    }

    fun importJSON(content: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.importJSONContent(content)
            _state.update { it.copy(isLoading = false) }
            result.onSuccess { count ->
                showImportMessage("Successfully imported $count channels from JSON!")
            }
            result.onFailure { error ->
                showImportMessage("Failed to import JSON: ${error.message}")
            }
        }
    }

    fun importFromUrl(url: String, format: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.importFromUrl(url, format)
            _state.update { it.copy(isLoading = false) }
            result.onSuccess { count ->
                showImportMessage("Successfully imported $count channels from link!")
            }
            result.onFailure { error ->
                showImportMessage("Failed loading URL: ${error.message}")
            }
        }
    }

    fun dismissImportMessage() {
        _state.update { it.copy(importMessage = null) }
    }

    private fun showImportMessage(msg: String) {
        _state.update { it.copy(importMessage = msg) }
    }

    // Helper functions for categories and grouping
    fun getCountries(): List<CountryItem> {
        val channels = _state.value.allChannels.filter { it.country != "Live Matches" }
        val uniqueCountryNames = channels.map { it.country }.distinct()
        
        return uniqueCountryNames.map { countryName ->
            val count = channels.count { it.country.equals(countryName, ignoreCase = true) }
            CountryItem(
                name = countryName,
                flag = getCountryEmojiFlag(countryName),
                channelCount = count
            )
        }.filter { it.channelCount > 0 }
    }

    fun getChannelsForCategory(categoryName: String): List<ChannelEntity> {
        val allChannels = _state.value.allChannels
        return when (categoryName.lowercase().trim()) {
            "sports" -> {
                allChannels.filter {
                    it.category.lowercase() == "sports" ||
                    it.name.lowercase().contains("sport") ||
                    it.name.lowercase().contains("bein") ||
                    it.name.lowercase().contains("arryadia") ||
                    it.name.lowercase().contains("alkass") ||
                    it.name.lowercase().contains("koora") ||
                    it.name.lowercase().contains("kora") ||
                    it.name.lowercase().contains("ssc")
                }
            }
            "arabic news" -> {
                allChannels.filter {
                    it.category.lowercase().contains("news") ||
                    it.category.lowercase().contains("akhbar") ||
                    it.name.lowercase().contains("news") ||
                    it.name.lowercase().contains("akhbar") ||
                    it.name.lowercase().contains("al jazeera") ||
                    it.name.lowercase().contains("al-jazeera") ||
                    it.name.lowercase().contains("al arabiya") ||
                    it.name.lowercase().contains("hadath") ||
                    it.name.lowercase().contains("cna") ||
                    it.name.lowercase().contains("al24") ||
                    it.name.lowercase().contains("france 24") ||
                    it.name.lowercase().contains("bbc arabic") ||
                    it.name.lowercase().contains("sky news")
                }
            }
            "documentaries" -> {
                allChannels.filter {
                    it.category.lowercase().contains("document") ||
                    it.category.lowercase().contains("doc") ||
                    it.category.lowercase().contains("wathaiqi") ||
                    it.name.lowercase().contains("documentary") ||
                    it.name.lowercase().contains("documentaires") ||
                    it.name.lowercase().contains("nat geo") ||
                    it.name.lowercase().contains("national geographic") ||
                    it.name.lowercase().contains("discovery") ||
                    it.name.lowercase().contains("wathaiqi") ||
                    it.name.lowercase().contains("wathaeqia") ||
                    it.name.lowercase().contains("watha'eqi")
                }
            }
            else -> {
                allChannels.filter { it.category.equals(categoryName, ignoreCase = true) }
            }
        }
    }

    fun getLiveMatches(): List<ChannelEntity> {
        // Find channels belonging to "Live Matches" or category "Sports" / starting with live tag
        return _state.value.allChannels.filter { 
            it.country.lowercase() == "live matches" || 
            it.category.lowercase() == "sports" ||
            it.name.startsWith("🔴")
        }
    }

    fun getBeInSportsChannels(): List<ChannelEntity> {
        val allChannels = _state.value.allChannels
        val favIds = _state.value.favorites.map { it.id }.toSet()
        
        val beinChannels = allChannels.filter { 
            it.country.equals("beIN SPORTS", ignoreCase = true) ||
            it.category.equals("beIN SPORTS", ignoreCase = true)
        }
        
        val prefs = getApplication<Application>().getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
        val server = prefs.getString("server", "http://t77ert.top:8080")?.trim() ?: "http://t77ert.top:8080"
        val username = prefs.getString("username", "190449942528177")?.trim() ?: "190449942528177"
        val password = prefs.getString("password", "UCoJgCCNnaK87n8")?.trim() ?: "UCoJgCCNnaK87n8"
        
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        
        return beinChannels.map { channel ->
            val resolvedUrl = if (channel.streamUrl.startsWith("bein_id:")) {
                val streamId = channel.streamUrl.substringAfter("bein_id:")
                "${cleanServer}/live/${username}/${password}/${streamId}.m3u8"
            } else {
                channel.streamUrl
            }
            channel.copy(
                streamUrl = resolvedUrl,
                isFavorite = favIds.contains(channel.id)
            )
        }
    }

    fun getServer(): String {
        val prefs = getApplication<Application>().getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
        return prefs.getString("server", "http://t77ert.top:8080") ?: "http://t77ert.top:8080"
    }

    fun getUsername(): String {
        val prefs = getApplication<Application>().getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
        return prefs.getString("username", "190449942528177") ?: "190449942528177"
    }

    fun getPassword(): String {
        val prefs = getApplication<Application>().getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
        return prefs.getString("password", "UCoJgCCNnaK87n8") ?: "UCoJgCCNnaK87n8"
    }

    fun saveConfig(server: String, username: String, password: String) {
        val prefs = getApplication<Application>().getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("server", server.trim())
            putString("username", username.trim())
            putString("password", password.trim())
            apply()
        }
        showImportMessage("Configuration saved successfully!")
    }

    fun getCountryEmojiFlag(countryName: String): String {
        return when (countryName.lowercase().trim()) {
            "morocco", "ma", "maroc" -> "🇲🇦"
            "algeria", "dz" -> "🇩🇿"
            "tunisia", "tn" -> "🇹🇳"
            "libya", "ly" -> "🇱🇾"
            "egypt", "eg" -> "🇪🇬"
            "saudi arabia", "sa" -> "🇸🇦"
            "united arab Emirates", "ae", "uae" -> "🇦🇪"
            "qatar", "qa" -> "🇶🇦"
            "kuwait", "kw" -> "🇰🇼"
            "bahrain", "bh" -> "🇧🇭"
            "oman", "om" -> "🇴🇲"
            "jordan", "jo" -> "🇯🇴"
            "palestine", "ps" -> "🇵🇸"
            "lebanon", "lb" -> "🇱🇧"
            "syria", "sy" -> "🇸🇾"
            "iraq", "iq" -> "🇮🇶"
            "yemen", "ye" -> "🇾🇪"
            "sudan", "sd" -> "🇸🇩"
            "mauritania", "mr" -> "🇲🇷"
            "live matches" -> "🔴"
            else -> "🏳️"
        }
    }
}
