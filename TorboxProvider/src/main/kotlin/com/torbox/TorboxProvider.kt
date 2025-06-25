package com.torbox // Changed package name

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import android.content.Context

class TorboxProvider(val plugin: TorboxPlugin) : MainAPI() { // Changed class name, added plugin constructor
    override var mainUrl = "https://torbox.app" // Updated mainUrl
    override var name = "Torbox" // Updated name
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Torrent) // Added Torrent type

    override var lang = "en"

    // Will enable once homepage functionality is clearer for Torbox
    override val hasMainPage = false

    // Data classes for API response
    data class TitleParsedData(
        val year: Int?,
        val title: String?,
        val encoder: String?,
        val site: String?
        // Add other fields if present and needed e.g. season, episode
    )

    data class NzbItem(
        val hash: String,
        val raw_title: String,
        val title: String?,
        val title_parsed_data: TitleParsedData?,
        val magnet: String?,
        val last_known_seeders: Int?,
        val last_known_peers: Int?,
        val size: Long?, // Assuming size is in bytes
        val tracker: String?,
        val categories: List<String>?,
        val files: List<String>?, // Assuming list of file names or paths
        val type: String?, // "usenet" or "torrent"
        val nzb: String?,
        val cached: Boolean? = null, // Added from API doc
        val owned: Boolean? = null // Added from API doc
    )

    data class TorboxSearchData(
        val metadata: Any?, // Type Any? as structure is unknown or can be null
        val nzbs: List<NzbItem>?,
        val time_taken: Double?,
        val cached: Boolean?,
        val total_nzbs: Int?
    )

    data class TorboxSearchResponse(
        val success: Boolean,
        val message: String?,
        val data: TorboxSearchData?
    )

    // Updated Torrent Info data classes based on provided API doc
    data class TorrentInfoFile( // Renamed from TorrentFile and fields adjusted
        val name: String?,
        val size: Long? // Was length, renamed to size as per API doc
        // path is part of name in API doc like "Big Buck Bunny/Big Buck Bunny.en.srt"
    )

    data class TorrentInfoData(
        val name: String?,
        val hash: String?,
        // magnet is not in the torrentinfo response sample, but might be useful if present elsewhere
        // val magnet: String?,
        val size: Long?, // Total size of the torrent
        val trackers: List<String>?, // List of tracker URLs
        val seeds: Int?,
        val peers: Int?,
        val files: List<TorrentInfoFile>?,
        // comment and created_date are not in the provided sample, removing for now
        // val comment: String?,
        // val created_date: Long?, // Timestamp
        val error: String? = null, // Kept for internal error tracking if needed by parsing logic
        val message: String? = null // Kept for internal message tracking if needed by parsing logic
    )

    // Updated to match the /torrentinfo response structure from problem description
    data class TorboxTorrentInfoResponse(
        val success: Boolean?,
        val error: String?, // Error message from API
        val detail: String?, // Detail message from API
        val data: TorrentInfoData?
    )

    // Data classes for /asynccreatetorrent
    data class AsyncCreateTorrentRequest(val magnet: String, val as_queued: Boolean = false) // as_queued might be useful
    data class AsyncCreateTorrentResponse(
        val success: Boolean?,
        val message: String?,
        val data: CreatedTorrentData? // Assuming 'data' contains info about the created torrent task
    )
    data class CreatedTorrentData(
        val id: Int?, // This is likely the torrent_id we need
        val hash: String?,
        // other fields like status, etc.
        val error: String? = null,
        val detail: String? = null // Sometimes errors are in detail
    )

    // Data classes for /mylist response
    // This will be a list of MyListItem or similar structure
    data class MyListResponse(
        val success: Boolean?,
        val data: List<MyListItem>?, // Assuming it's a list. API docs say "get my torrent list"
                                     // The actual structure of items in 'data' needs to be inferred or found in more detailed docs
        val message: String?
    )
    data class MyListItemFile( // Structure for files within a torrent in mylist
        val id: Int?, // Crucial: file_id for requestdl
        val name: String?,
        val path: String?, // Or List<String> for path components
        val size: Long?,
        val downloaded: Boolean?, // Or status string
        val streamable_link: String? // Some APIs provide this directly here
    )
    data class MyListItem( // Structure of a single torrent in /mylist
        val id: Int?, // torrent_id
        val name: String?,
        val hash: String?,
        val size: Long?,
        val status: String?, // e.g., "downloading", "completed", "cached", "error"
        val progress: Float?, // e.g. 0.0 to 100.0
        val files: List<MyListItemFile>?,
        val error_message: String?
    )

    // Updated Data class for /requestdl response as per problem description
    data class RequestDlResponse(
        val success: Boolean?,
        val error: String?, // Error message from API
        val detail: String?, // Detail message from API (can be success or error detail)
        val data: String? // This field now holds the download URL
        // val message: String? // Removed as 'detail' seems to cover this based on samples
    )


    private fun getApiKey(): String? {
        return plugin.activity?.getSharedPreferences(SettingsFragment.PREFS_FILE, Context.MODE_PRIVATE)
            ?.getString(SettingsFragment.TORBOX_API_KEY, null)
    }

    private fun getAuthHeaders(apiKey: String): Map<String, String> {
        return mapOf("Authorization" to "Bearer $apiKey")
    }

    // This function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val apiKey = getApiKey()
        if (apiKey.isNullOrEmpty()) {
            // Optionally, notify the user that API key is missing.
            // For now, just return empty list or throw an error.
            // plugin.activity?.runOnUiThread {
            //     CommonActivity.showToast(plugin.activity, "Torbox API Key is not set", Toast.LENGTH_LONG)
            // }
            return emptyList()
        }

        val headers = getAuthHeaders(apiKey)
        val searchUrl = "https://search-api.torbox.app/torrents/search/$query"

        // Parameters as per Torbox API documentation
        val params = mapOf(
            "metadata" to "true",
            "check_cache" to "true",
            "check_owned" to "true"
            // "search_user_engines" to "false" // Optional, defaults to false
        )

        return try {
            val response = app.get(searchUrl, params = params, headers = headers).text
            val torboxResponse = parseJson<TorboxSearchResponse>(response)

            if (torboxResponse.success) {
                torboxResponse.data?.nzbs?.mapNotNull { it.toSearchResponse() } ?: emptyList()
            } else {
                // Log error or notify user
                // plugin.activity?.runOnUiThread {
                //     CommonActivity.showToast(plugin.activity, "Torbox search error: ${torboxResponse.message}", Toast.LENGTH_LONG)
                // }
                emptyList()
            }
        } catch (e: Exception) {
            // Log error or notify user
            // e.printStackTrace()
            // plugin.activity?.runOnUiThread {
            //    CommonActivity.showToast(plugin.activity, "Failed to fetch from Torbox: ${e.message}", Toast.LENGTH_LONG)
            // }
            emptyList()
        }
    }

    private fun NzbItem.toSearchResponse(): SearchResponse? {
        val displayTitle = this.title ?: this.raw_title

        // More robust TvType inference
        val type = when {
            this.categories?.any { cat -> cat.contains("movie", ignoreCase = true) } == true -> TvType.Movie
            this.categories?.any { cat -> cat.contains("tv", ignoreCase = true) || cat.contains("series", ignoreCase = true) } == true -> TvType.TvSeries
            this.type.equals("torrent", ignoreCase = true) -> TvType.Torrent
            // Usenet items might not map directly or might need a new TvType if desired
            this.type.equals("usenet", ignoreCase = true) -> null // Or map to a generic type if they can be processed
            else -> TvType.Torrent // Default fallback
        } ?: return null // If type is null (e.g. unhandled usenet), skip this item

        val itemUrl = this.magnet ?: "hash:${this.hash}"

        // Basic quality parsing from title or encoder info. Can be expanded.
        val quality = getQualityFromString(this.title_parsed_data?.encoder ?: this.raw_title)

        return when (type) {
            TvType.Movie -> newMovieSearchResponse(displayTitle, itemUrl, type) {
                this.posterUrl = null // No poster in API sample
                this.year = title_parsed_data?.year
                this.quality = quality
                // Add other movie specific details if available
            }
            TvType.TvSeries -> newTvSeriesSearchResponse(displayTitle, itemUrl, type) {
                this.posterUrl = null
                this.year = title_parsed_data?.year
                this.quality = quality
                // Add season/episode information if parseable from title_parsed_data or raw_title
            }
            TvType.Torrent -> newTorrentSearchResponse(displayTitle, itemUrl, type) {
                this.posterUrl = null // No poster for torrents usually
                this.quality = quality
                this.size = this@toSearchResponse.size
                this.seeds = this@toSearchResponse.last_known_seeders
                this.peers = this@toSearchResponse.last_known_peers
            }
            else -> null // Should not happen due to earlier null check
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val apiKey = getApiKey()
        if (apiKey.isNullOrEmpty()) {
            // Notify user or log
            return null
        }


        if (url.startsWith("magnet:")) {
            // Try to parse display name (dn) from magnet for a better title
            val magnetName = url.substringAfter("dn=").substringBefore("&")
                .let { if (it.isNotEmpty()) java.net.URLDecoder.decode(it, "UTF-8") else "Torrent from magnet" }

            // If we have a magnet, we pass it directly to loadLinks via TorrentLoadResponse.
            // `loadLinks` will be responsible for adding it to Torbox.
            // We could optionally call /torrentinfo here if we wanted to display files *before* adding,
            // but it's simpler to let loadLinks handle the "add then get files" flow.
            return newTorrentLoadResponse(
                name = magnetName,
                url = url, // url for the load response is the magnet uri itself
                type = TvType.Torrent, // Or infer better from search result if possible to pass more data
                link = url // The data passed to loadLinks will be the magnet uri
            )
        } else if (url.startsWith("hash:")) {
            val hash = url.removePrefix("hash:")
            val torrentInfoUrl = "https://api.torbox.app/v1/api/torrents/torrentinfo"
            val params = mapOf("hash" to hash)
            val authHeaders = getAuthHeaders(apiKey) // Added this line

            try {
                val response = app.get(torrentInfoUrl, params = params, headers = authHeaders).text // Use authHeaders
                // The openapi.json for /v1/api/torrents/torrentinfo doesn't specify response schema.
                // Assuming it's similar to TorboxTorrentInfoResponse or directly TorrentInfoData
                // Let's try parsing as TorrentInfoData first, if it's nested under "data", adjust.
                // It's common for APIs to return the object directly or under a "data" field.
                // Parse the response using the updated TorboxTorrentInfoResponse structure
                val apiResponse = parseJson<TorboxTorrentInfoResponse>(response)
                val torrentInfo = apiResponse.data

                if (apiResponse.success != true || torrentInfo == null) {
                    // Log error from apiResponse.error or apiResponse.detail
                    // plugin.activity?.runOnUiThread {
                    //     CommonActivity.showToast(plugin.activity, "Torbox torrent info error: ${apiResponse.error ?: apiResponse.detail}", Toast.LENGTH_LONG)
                    // }
                    return null // Failed to get info or critical info missing
                }

                val torrentName = torrentInfo.name ?: "Torrent for $hash"
                // Construct magnet link as it's not in the /torrentinfo response
                // Adding dn (display name) is good practice for magnet links.
                val magnetLink = "magnet:?xt=urn:btih:${hash}&dn=${java.net.URLEncoder.encode(torrentName, "UTF-8")}"


                // We got torrent info. Now create a TorrentLoadResponse.
                // The 'url' of LoadResponse should be something unique for this item.
                // Using the magnet link itself is consistent.
                return newTorrentLoadResponse(
                    name = torrentName,
                    url = magnetLink, // Use the constructed magnet link as the identifier/url
                    type = TvType.Torrent, // Or try to infer better if more info in TorrentInfoData
                    link = magnetLink // This is what's passed to loadLinks
                ) {
                    // Populate other fields if available from torrentInfo
                    // this.posterUrl = ... (if available)
                    // this.year = ... (if available)
                    // this.plot = ...
                    this.torrentInfo?.size = torrentInfo.size
                    this.torrentInfo?.seeds = torrentInfo.seeds
                    this.torrentInfo?.peers = torrentInfo.peers
                    // Example of how files could be potentially mapped if LoadResponse supported it directly here
                    // this.episodes = torrentInfo.files?.mapIndexedNotNull { index, file ->
                    //    Episode(
                    //        data = file.name ?: "File ${index + 1}", // Or some unique identifier for the file
                    //        name = file.name ?: "File ${index + 1}",
                    //        episode = index + 1,
                    //        posterUrl = null,
                    //        rating = null,
                    //        description = null
                    //    ).apply { this.fileSize = file.size }
                    // }
                }
            } catch (e: Exception) {
                // Log network or parsing error for /torrentinfo
                // e.printStackTrace()
                // plugin.activity?.runOnUiThread {
                //    CommonActivity.showToast(plugin.activity, "Failed to fetch torrent info from Torbox: ${e.message}", Toast.LENGTH_LONG)
                // }
                return null
            }
        }
        return null // Should not be reached if url is always magnet or hash
    }

    override suspend fun loadLinks(
        data: String, // This is the magnet URI from LoadResponse.link
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val apiKey = getApiKey()
        if (apiKey.isNullOrEmpty()) {
            // Log or notify: API key missing
            return false
        }
        val authHeaders = getAuthHeaders(apiKey) // Use authHeaders

        var torrentId: Int? = null

        // 1. Add torrent to Torbox using /asynccreatetorrent
        try {
            val addTorrentUrl = "https://api.torbox.app/v1/api/torrents/asynccreatetorrent"
            // The openapi.json implies multipart/form-data for this endpoint,
            // but typically for magnet links, a JSON body with {"magnet": "..."} is also common or preferred.
            // Cloudstream's app.post with `json` parameter sends application/json.
            // If Torbox strictly requires multipart for magnets, this needs adjustment (e.g. using `data` param for form-urlencoded or specific multipart builder).
            // For now, assuming JSON is fine or that `app.post` handles magnet correctly for Torbox.
            // The schema Body_async_create_torrent_v1_api_torrents_asynccreatetorrent_post has 'magnet' as a property.

            val requestBody = AsyncCreateTorrentRequest(magnet = data)
            val responseText = app.post(
                addTorrentUrl,
                headers = authHeaders, // Use authHeaders
                json = requestBody // Sending as JSON
            ).text

            val createResponse = parseJson<AsyncCreateTorrentResponse>(responseText)

            if (createResponse.success != true && createResponse.data?.id == null) {
                var errorMsg = createResponse.message ?: createResponse.data?.error ?: createResponse.data?.detail ?: "Failed to add torrent"
                // Log or notify: errorMsg
                return false
            }
            torrentId = createResponse.data?.id
            if (torrentId == null) {
                 // Log or notify: "Torrent added but ID not found in response"
                return false
            }
        } catch (e: Exception) {
            // Log or notify: "Error adding torrent to Torbox: ${e.message}"
            return false
        }

        // 2. Get torrent status and files using /mylist (potentially poll)
        // For simplicity, we'll try once. Real-world might need polling or delay.
        var torrentDetails: MyListItem? = null
        try {
            // Delay briefly to allow Torbox to process the async add, if necessary.
            // kotlinx.coroutines.delay(5000) // Example: 5 second delay - requires coroutine context

            val myListUrl = "https://api.torbox.app/v1/api/torrents/mylist"
            val params = mapOf("id" to torrentId.toString()) // Filter by our torrent ID
            val myListResponseText = app.get(myListUrl, headers = authHeaders, params = params).text // Use authHeaders
            val myList = parseJson<MyListResponse>(myListResponseText)

            if (myList.success == true && myList.data != null) {
                torrentDetails = myList.data.firstOrNull { it.id == torrentId }
            }
            if (torrentDetails == null) {
                // Log or notify: "Torrent not found in user's list after adding, or error fetching list."
                return false
            }
            // Check torrentDetails.status - e.g. "completed", "cached", "downloading"
            // If "error", "stalled", etc. then fail or handle appropriately
            if (torrentDetails.status == "error" || torrentDetails.error_message != null) {
                // Log or notify: "Torrent has error: ${torrentDetails.error_message}"
                return false
            }
            // If not yet ready (e.g. still downloading and not file is marked streamable soon),
            // we might need to inform the user or implement polling.
            // For now, proceed if files are listed.

        } catch (e: Exception) {
            // Log or notify: "Error fetching torrent details from Torbox: ${e.message}"
            return false
        }

        // 3. For each file, get a streamable link using /requestdl
        var linksFound = false
        torrentDetails.files?.forEach { file ->
            if (file.id == null || file.name == null) {
                // Log: "File ID or name is null, skipping"
                return@forEach // continue to next file
            }

            try {
                val requestDlUrl = "https://api.torbox.app/v1/api/torrents/requestdl"
                // API key will be passed in authHeaders
                val dlParams = mapOf(
                    "torrent_id" to torrentId.toString(),
                    "file_id" to file.id.toString(),
                    "redirect" to "true" // Added redirect=true for permalinks
                )
                val dlResponseText = app.get(requestDlUrl, headers = authHeaders, params = dlParams).text
                val dlResponse = parseJson<RequestDlResponse>(dlResponseText)

                // Use dlResponse.data for the URL and check it
                if (dlResponse.success == true && !dlResponse.data.isNullOrEmpty()) {
                    val downloadLink = dlResponse.data
                    val quality = getQualityFromString(file.name)
                    callback.invoke(
                        ExtractorLink(
                            source = name, // Provider name
                            name = file.name,
                            url = downloadLink, // Use data field for the URL
                            referer = mainUrl, // Torbox main URL as referer
                            quality = quality.value,
                            isM3u8 = downloadLink.contains(".m3u8", ignoreCase = true)
                            // Add other details if available/needed e.g. size
                        )
                    )
                    linksFound = true
                } else {
                    // Log or notify: "Failed to get download link for file ${file.name}: ${dlResponse.message ?: dlResponse.error}"
                }
            } catch (e: Exception) {
                // Log or notify: "Error getting download link for file ${file.name}: ${e.message}"
            }
        }

        return linksFound
    }
}
