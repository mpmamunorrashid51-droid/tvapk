package com.example

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerEntry(
    val name: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class LiveChannel(
    val id: String,
    val name: String,
    val category: String, // "cricket", "football", "bangladesh", "india", "news", "othersports"
    val logoUrl: String,
    val isActive: Boolean = true,
    val servers: List<ServerEntry>,
    val currentMatchTitle: String = "Live Streaming Available"
)

object DefaultChannels {
    fun getList(): List<LiveChannel> = listOf(
        // Cricket
        LiveChannel(
            id = "cricket_01",
            name = "GTV Sports HD",
            category = "cricket",
            logoUrl = "https://source.unsplash.com/100x100/?cricket,stadium",
            servers = listOf(
                ServerEntry("Server 1 (Primary - Ultra HD)", "aHR0cHM6Ly9raGVsYTM2NS5saXZlL3N0cmVhbS9jcmtfcHJpbWFyeS5tM3U4"),
                ServerEntry("Server 2 (Backup - 1080p)", "aHR0cHM6Ly9raGVsYTM2NS5saXZlL3N0cmVhbS9jcmtfc2VjL2xpdmUubTN1OA=="),
                ServerEntry("Server 3 (Low Latency - SD)", "aHR0cHM6Ly9raGVsYTM2NS1iYWNrdXAubmV0L2xpdmUvY3JrXzI0M3Auc3RyZWFtL2luZGV4Lm0zdTg=")
            ),
            currentMatchTitle = "ICC T20: Bangladesh vs Pakistan Live"
        ),
        LiveChannel(
            id = "cricket_02",
            name = "T Sports Live",
            category = "cricket",
            logoUrl = "https://source.unsplash.com/100x100/?cricket,ball",
            servers = listOf(
                ServerEntry("Server 1 (Main Stream)", "aHR0cHM6Ly9raGVsYTM2NS5saXZlL3N0cmVhbS90c3BvcnRzXzAxLm0zdTg="),
                ServerEntry("Server 2 (Backup 720p)", "aHR0cHM6Ly9idWZmZXIubTN1OC5saXZlL3Nwb3J0cy90c3BvcnRzXzcyMHAubTN1OA==")
            ),
            currentMatchTitle = "T20 Big Bash League Live"
        ),
        LiveChannel(
            id = "cricket_03",
            name = "Star Sports 1 HD",
            category = "cricket",
            logoUrl = "https://source.unsplash.com/100x100/?cricket,pitch",
            servers = listOf(
                ServerEntry("Server 1 (Fast Feed)", "aHR0cHM6Ly9raGVsYTM2NS5saXZlL3N0cmVhbS9zdGFyc3BvcnRzMS5tM3U4"),
                ServerEntry("Server 2 (Alternative)", "aHR0cHM6Ly9jZG4uNjg2OC5jb20vc3BvcnRzLWxpdmUvY3JrMTAubTN1OA==")
            ),
            currentMatchTitle = "India Tour of Australia - 1st Test"
        ),
        
        // Football
        LiveChannel(
            id = "foot_01",
            name = "Sony Ten 2 HD",
            category = "football",
            logoUrl = "https://source.unsplash.com/100x100/?football,soccer",
            servers = listOf(
                ServerEntry("Server 1 (High Quality)", "aHR0cHM6Ly9raGVsYTM2NS5saXZlL3N0cmVhbS9mb290YmFsbF9zb255Lm0zdTg="),
                ServerEntry("Server 2 (Backup)", "aHR0cHM6Ly9zZXJ2ZXIyLm0zdTgvc3RyZWFtL3RlbnMyLm0zdTg=")
            ),
            currentMatchTitle = "UEFA Champions League: Real Madrid vs Man City"
        ),
        LiveChannel(
            id = "foot_02",
            name = "ESPN Live",
            category = "football",
            logoUrl = "https://source.unsplash.com/100x100/?football,goal",
            servers = listOf(
                ServerEntry("Server 1 (English Audio)", "aHR0cHM6Ly9raGVsYTM2NS5saXZlL3N0cmVhbS9lc3BuX2Zvb3Rib2xsLm0zdTg="),
                ServerEntry("Server 2 (Spanish Audio)", "aHR0cHM6Ly9lc3BuLWxhdGFtLm0zdTgvc3RyZWFtXzIubTN1OA==")
            ),
            currentMatchTitle = "La Liga: Barcelona vs Atletico Madrid"
        ),

        // Bangladeshi
        LiveChannel(
            id = "bd_01",
            name = "BTV World",
            category = "bangladesh",
            logoUrl = "https://source.unsplash.com/100x100/?bangladesh,flag",
            servers = listOf(
                ServerEntry("Server 1 (Government Feed)", "aHR0cHM6Ly9idHZ3LmxpdmUtc3RyZWFtLmJkL2hscy9idHZ3b3JsZC5tM3U4"),
                ServerEntry("Server 2 (Backup)", "aHR0cHM6Ly9iaGFzYW4tc3RyZWFtLmNvbS9idHZ3L2luZGV4Lm0zdTg=")
            ),
            currentMatchTitle = "National Sports Awards Special Broadcast"
        ),
        LiveChannel(
            id = "bd_02",
            name = "Channel i",
            category = "bangladesh",
            logoUrl = "https://source.unsplash.com/100x100/?green,nature",
            servers = listOf(
                ServerEntry("Server 1", "aHR0cHM6Ly9zaGFmaXN0cmVhbS5jb20vY2hhbm5lbGkvbWFpbi5tM3U4"),
                ServerEntry("Server 2", "aHR0cHM6Ly8xMTIuMjAyLjEzMS44MC9obHMvY2hhbm5lbGkvaW5kZXgubTN1OA==")
            ),
            currentMatchTitle = "Desh-Bidesh Sports Coverage"
        ),

        // Indian
        LiveChannel(
            id = "in_01",
            name = "Star Sports Select 1",
            category = "india",
            logoUrl = "https://source.unsplash.com/100x100/?india,cricket",
            servers = listOf(
                ServerEntry("Server 1 (Select HD)", "aHR0cHM6Ly9zdGFyc3BvcnRzLnNlcnZlci9zZWxlY3QxLm0zdTg="),
                ServerEntry("Server 2 (Backup SD)", "aHR0cHM6Ly9raGVsYTM2NXMubmV0L2luX3R2L3NzX3NlbGVjdC5tM3U4")
            ),
            currentMatchTitle = "F1 GP Highlights Live"
        ),
        LiveChannel(
            id = "in_02",
            name = "Sony Six HD",
            category = "india",
            logoUrl = "https://source.unsplash.com/100x100/?jersey,sports",
            servers = listOf(
                ServerEntry("Server 1 (Premium)", "aHR0cHM6Ly9zaXguaHR0cC1jZG4ubmV0L3NpeF9saXZlLm0zdTg="),
                ServerEntry("Server 2 (Backup)", "aHR0cHM6Ly9raGVsYTM2NS5saXZlL3NpeF9iYWNrdXAubTN1OA==")
            ),
            currentMatchTitle = "Pro Kabaddi League Live"
        ),

        // News
        LiveChannel(
            id = "news_01",
            name = "Somoy News TV",
            category = "news",
            logoUrl = "https://source.unsplash.com/100x100/?news,studio",
            servers = listOf(
                ServerEntry("Server 1 (Somoy Main)", "aHR0cHM6Ly9zb21veS5jZG4teW91dHViZS5jb20vaGxzL25ld3MubTN1OA=="),
                ServerEntry("Server 2 (Mirror Feed)", "aHR0cHM6Ly9jZG4tc215LmxpdmUvc3RyZWFtMS5tM3U4")
            ),
            currentMatchTitle = "Sports Roundups & World Cup Special Analysis"
        ),
        LiveChannel(
            id = "news_02",
            name = "Jamuna TV Live",
            category = "news",
            logoUrl = "https://source.unsplash.com/100x100/?microphone,press",
            servers = listOf(
                ServerEntry("Server 1 (Direct)", "aHR0cHM6Ly9qYW11bmEuaGxzLnRyYWZmaWMuaW8vbGl2ZS5tM3U4"),
                ServerEntry("Server 2 (Quality B)", "aHR0cHM6Ly9iYWNrdXA4ODkuY2R0Lm5ldC9qYW11bmEvaW5kZXgubTN1OA==")
            ),
            currentMatchTitle = "Khela Sports Talk Hour"
        ),

        // Other Sports
        LiveChannel(
            id = "othersports_01",
            name = "F1 TV Pro HD",
            category = "othersports",
            logoUrl = "https://source.unsplash.com/100x100/?racing,car",
            servers = listOf(
                ServerEntry("Server 1 (Main Race Feed)", "aHR0cHM6Ly9mMXR2LnNvbm8yLmNvbS9obHMvZjEubTN1OA=="),
                ServerEntry("Server 2 (Onboards Channel)", "aHR0cHM6Ly9mMXR2Lm9uYm9hcmRzLnNlcnZlci9obHMvY2FyLm0zdTg=")
            ),
            currentMatchTitle = "Formula 1: Monaco Grand Prix Live Practice"
        ),
        LiveChannel(
            id = "othersports_02",
            name = "MotoGP Live Feed",
            category = "othersports",
            logoUrl = "https://source.unsplash.com/100x100/?motorcycle,race",
            servers = listOf(
                ServerEntry("Server 1", "aHR0cHM6Ly9tb3RvZ3AtbGl2ZS5jZG4uY29tL3N0cmVhbS9saXZlLm0zdTg="),
                ServerEntry("Server 2", "aHR0cHM6Ly9zaGFmaXN0cmVhbS5jb20vbW90b2dwL2luZGV4Lm0zdTg=")
            ),
            currentMatchTitle = "Austrian MotoGP Warm Up Live"
        )
    )
}
