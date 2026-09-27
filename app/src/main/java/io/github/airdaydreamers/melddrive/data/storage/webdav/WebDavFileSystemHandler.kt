package io.github.airdaydreamers.melddrive.data.storage.webdav

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageException
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.storage.StorageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import timber.log.Timber
import java.io.IOException
import java.io.StringReader
import java.io.UnsupportedEncodingException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

open class WebDavFileSystemHandler @AssistedInject constructor(@Assisted private val server: RemoteServer, private val client: OkHttpClient) : StorageSource {

    @AssistedFactory
    interface Factory {
        fun create(server: RemoteServer): WebDavFileSystemHandler
    }

    constructor(server: RemoteServer) : this(
        server = server,
        client = WebDavHttpClientFactory.createClient(server),
    )

    private val baseUrl: String by lazy { buildBaseUrl(server) }

    override suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val targetUrl = resolveUrl(path, isDirectory = true)
        Timber.d("WebDavFileSystemHandler: listFiles path='%s' -> targetUrl='%s'", path, targetUrl)

        val propfindXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength/>
                    <D:getlastmodified/>
                    <D:displayname/>
                </D:prop>
            </D:propfind>
        """.trimIndent()

        val request = Request.Builder()
            .url(targetUrl)
            .method("PROPFIND", propfindXml.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Timber.d(
                    "WebDavFileSystemHandler: listFiles response code=%d, message='%s', headers=%s",
                    response.code,
                    response.message,
                    response.headers,
                )
                if (!response.isSuccessful && response.code != HTTP_MULTI_STATUS) {
                    val errorBody = response.body?.string().orEmpty()
                    val authHeader = response.header("WWW-Authenticate")
                    Timber.e(
                        "WebDavFileSystemHandler: PROPFIND failed code=%d message='%s' WWW-Authenticate='%s' body='%s'",
                        response.code,
                        response.message,
                        authHeader,
                        errorBody,
                    )
                    throw StorageException("Failed to list directory: HTTP ${response.code} (${response.message})")
                }

                val bodyString = response.body?.string().orEmpty()
                Timber.d("WebDavFileSystemHandler: listFiles response body length=%d", bodyString.length)
                val parsedItems = parsePropfindResponse(bodyString, path)

                val rootNormalizedPath = normalizePath(path)
                val filtered = parsedItems.filter { item ->
                    val itemNormalized = normalizePath(item.path)
                    itemNormalized != rootNormalizedPath && itemNormalized.isNotEmpty()
                }
                Timber.d("WebDavFileSystemHandler: listFiles returning %d items (after filtering root '%s')", filtered.size, rootNormalizedPath)
                filtered
            }
        } catch (e: StorageException) {
            Timber.e(e, "WebDavFileSystemHandler: listFiles StorageException for path='%s' targetUrl='%s'", path, targetUrl)
            throw e
        } catch (e: IOException) {
            Timber.e(e, "WebDavFileSystemHandler: listFiles IOException for path='%s' targetUrl='%s'", path, targetUrl)
            val message = formatNetworkErrorMessage(e, targetUrl)
            throw StorageException(message, e)
        }
    }

    override suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = resolveUrl(path)
        Timber.d("WebDavFileSystemHandler: deleteFile path='%s' -> targetUrl='%s'", path, targetUrl)

        val request = Request.Builder()
            .url(targetUrl)
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Timber.d("WebDavFileSystemHandler: deleteFile response code=%d message='%s'", response.code, response.message)
                if (!response.isSuccessful && response.code != HTTP_NO_CONTENT && response.code != HTTP_ACCEPTED) {
                    val errorBody = response.body?.string().orEmpty()
                    Timber.e("WebDavFileSystemHandler: deleteFile failed code=%d message='%s' body='%s'", response.code, response.message, errorBody)
                    return@use false
                }
                true
            }
        } catch (e: IOException) {
            Timber.e(e, "WebDavFileSystemHandler: deleteFile IOException for path='%s'", path)
            false
        }
    }

    override suspend fun renameFile(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val sourceUrl = resolveUrl(path)
        val parentPath = path.trimEnd('/').substringBeforeLast('/', "")
        val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
        val destUrl = resolveUrl(newPath)

        Timber.d("WebDavFileSystemHandler: renameFile path='%s' to '%s' -> destUrl='%s'", path, newName, destUrl)

        val request = Request.Builder()
            .url(sourceUrl)
            .method("MOVE", null)
            .header("Destination", destUrl)
            .header("Overwrite", "T")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Timber.d("WebDavFileSystemHandler: renameFile response code=%d message='%s'", response.code, response.message)
                if (!response.isSuccessful && response.code != HTTP_CREATED && response.code != HTTP_NO_CONTENT) {
                    val errorBody = response.body?.string().orEmpty()
                    Timber.e("WebDavFileSystemHandler: renameFile failed code=%d message='%s' body='%s'", response.code, response.message, errorBody)
                    return@use false
                }
                true
            }
        } catch (e: IOException) {
            Timber.e(e, "WebDavFileSystemHandler: renameFile IOException for path='%s'", path)
            false
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val newFolderPath = if (parentPath.isEmpty() || parentPath == "/") name else "${parentPath.trimEnd('/')}/$name"
        val targetUrl = resolveUrl(newFolderPath)

        Timber.d("WebDavFileSystemHandler: createFolder parentPath='%s' name='%s' -> targetUrl='%s'", parentPath, name, targetUrl)

        val request = Request.Builder()
            .url(targetUrl)
            .method("MKCOL", null)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Timber.d("WebDavFileSystemHandler: createFolder response code=%d message='%s'", response.code, response.message)
                if (!response.isSuccessful && response.code != HTTP_CREATED) {
                    val errorBody = response.body?.string().orEmpty()
                    Timber.e("WebDavFileSystemHandler: createFolder failed code=%d message='%s' body='%s'", response.code, response.message, errorBody)
                    return@use false
                }
                true
            }
        } catch (e: IOException) {
            Timber.e(e, "WebDavFileSystemHandler: createFolder IOException for parentPath='%s' name='%s'", parentPath, name)
            false
        }
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val targetUrl = resolveUrl(path)
        Timber.d("WebDavFileSystemHandler: getFileSize path='%s' -> targetUrl='%s'", path, targetUrl)

        val propfindXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:getcontentlength/>
                </D:prop>
            </D:propfind>
        """.trimIndent()

        val request = Request.Builder()
            .url(targetUrl)
            .method("PROPFIND", propfindXml.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Timber.d("WebDavFileSystemHandler: getFileSize response code=%d message='%s'", response.code, response.message)
                if (!response.isSuccessful && response.code != HTTP_MULTI_STATUS) {
                    return@use 0L
                }

                val bodyString = response.body?.string().orEmpty()
                val parsedItems = parsePropfindResponse(bodyString, path)
                parsedItems.firstOrNull()?.size ?: 0L
            }
        } catch (e: IOException) {
            Timber.e(e, "WebDavFileSystemHandler: getFileSize IOException for path='%s'", path)
            0L
        }
    }

    override suspend fun readFile(path: String, offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        val targetUrl = resolveUrl(path)
        val endOffset = offset + length - 1
        val rangeHeader = "bytes=$offset-$endOffset"

        Timber.d("WebDavFileSystemHandler: readFile path='%s' range='%s' -> targetUrl='%s'", path, rangeHeader, targetUrl)

        val request = Request.Builder()
            .url(targetUrl)
            .header("Range", rangeHeader)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Timber.d("WebDavFileSystemHandler: readFile response code=%d message='%s'", response.code, response.message)
                if (!response.isSuccessful && response.code != HTTP_PARTIAL_CONTENT) {
                    val errorBody = response.body?.string().orEmpty()
                    Timber.e("WebDavFileSystemHandler: readFile failed HTTP code=%d message='%s' body='%s'", response.code, response.message, errorBody)
                    return@use ByteArray(0)
                }
                response.body?.bytes() ?: ByteArray(0)
            }
        } catch (e: IOException) {
            Timber.e(e, "WebDavFileSystemHandler: readFile IOException for path='%s' range='%s'", path, rangeHeader)
            ByteArray(0)
        }
    }

    override suspend fun searchFiles(path: String, query: String): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        recursiveSearch(path, query, results)
        results
    }

    private suspend fun recursiveSearch(currentPath: String, query: String, results: MutableList<FileItem>) {
        val items = try {
            listFiles(currentPath)
        } catch (_: StorageException) {
            emptyList()
        }

        for (item in items) {
            if (item.name.contains(query, ignoreCase = true)) {
                results.add(item)
            }
            if (item.isDirectory) {
                recursiveSearch(item.path, query, results)
            }
        }
    }

    private fun resolveUrl(relativePath: String, isDirectory: Boolean = false): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanRelative = relativePath.trim('/')
        return if (cleanRelative.isEmpty()) {
            "$cleanBase/"
        } else {
            val suffix = if (isDirectory) "/" else ""
            "$cleanBase/$cleanRelative$suffix"
        }
    }

    private fun normalizePath(p: String): String = p.trim('/').replace("//", "/")

    private fun parsePropfindResponse(xmlBody: String, requestedPath: String): List<FileItem> {
        if (xmlBody.isBlank()) {
            Timber.w("WebDavFileSystemHandler: parsePropfindResponse received blank XML body")
            return emptyList()
        }

        return try {
            val doc = parseXmlDocument(xmlBody)
            val responseNodes = getResponseNodes(doc)
            Timber.d("WebDavFileSystemHandler: Found %d response nodes in XML", responseNodes.length)

            val items = extractFileItems(responseNodes, requestedPath)
            if (items.isEmpty()) {
                logXmlSnippet(xmlBody, "XML parsed but 0 items extracted.")
            }
            items
        } catch (e: SAXException) {
            logXmlSnippet(xmlBody, "SAXException parsing PROPFIND XML: ${e.message}")
            emptyList()
        } catch (e: ParserConfigurationException) {
            logXmlSnippet(xmlBody, "ParserConfigurationException parsing PROPFIND XML: ${e.message}")
            emptyList()
        } catch (e: IOException) {
            logXmlSnippet(xmlBody, "IOException parsing PROPFIND XML: ${e.message}")
            emptyList()
        }
    }

    private fun parseXmlDocument(xmlBody: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(xmlBody)))
    }

    private fun getResponseNodes(doc: Document): NodeList {
        var responseList = doc.getElementsByTagNameNS("*", "response")
        if (responseList.length == 0) {
            responseList = doc.getElementsByTagName("response")
        }
        if (responseList.length == 0) {
            responseList = doc.getElementsByTagName("d:response")
        }
        return responseList
    }

    private fun extractFileItems(responseNodes: NodeList, requestedPath: String): List<FileItem> {
        val items = mutableListOf<FileItem>()
        for (i in 0 until responseNodes.length) {
            val responseNode = responseNodes.item(i) as? Element ?: continue
            val item = parseResponseNode(responseNode, requestedPath)
            if (item != null) {
                items.add(item)
            }
        }
        return items
    }

    private fun parseResponseNode(responseNode: Element, requestedPath: String): FileItem? {
        val props = extractResponseProps(responseNode)
        val pathFromHref = extractRelativePath(props.href, requestedPath)
        val name = if (props.displayName.isNotBlank()) {
            props.displayName
        } else {
            pathFromHref.trim('/').substringAfterLast('/')
        }
        val isHidden = name.startsWith(".")

        if (pathFromHref.isBlank() && name.isBlank()) return null

        return FileItem(
            path = pathFromHref,
            name = name,
            isDirectory = props.isDirectory,
            size = if (props.isDirectory) 0L else props.contentLength,
            lastModified = props.lastModified,
            isHidden = isHidden,
            storageType = StorageType.WEBDAV,
        )
    }

    private fun extractResponseProps(responseNode: Element): MutableResponseProps {
        val props = MutableResponseProps()
        val childNodes = responseNode.getElementsByTagNameNS("*", "*")

        for (j in 0 until childNodes.length) {
            val child = childNodes.item(j) as? Element ?: continue
            parsePropChild(child, responseNode, props)
        }

        if (props.href.isEmpty()) {
            props.href = extractHrefFallback(responseNode)
        }

        return props
    }

    private fun parsePropChild(child: Element, responseNode: Element, props: MutableResponseProps) {
        val tag = child.localName?.lowercase(Locale.ROOT)
            ?: child.tagName.substringAfterLast(':').lowercase(Locale.ROOT)

        when (tag) {
            "href" -> if (props.href.isEmpty() && child.parentNode == responseNode) {
                props.href = child.textContent.trim()
            }

            "displayname" -> if (props.displayName.isEmpty()) {
                props.displayName = child.textContent.trim()
            }

            "getcontentlength" -> if (props.contentLength == 0L) {
                props.contentLength = child.textContent.trim().toLongOrNull() ?: 0L
            }

            "getlastmodified" -> if (props.lastModified == 0L) {
                props.lastModified = parseHttpDate(child.textContent.trim())
            }

            "collection" -> props.isDirectory = true
        }
    }

    private fun extractHrefFallback(responseNode: Element): String {
        val hrefNodes = responseNode.getElementsByTagNameNS("*", "href")
        return if (hrefNodes.length > 0) hrefNodes.item(0).textContent.trim() else ""
    }

    private fun logXmlSnippet(xmlBody: String, reason: String) {
        val preview = if (xmlBody.length > LOG_PREVIEW_LIMIT) {
            xmlBody.substring(0, LOG_PREVIEW_LIMIT) + "..."
        } else {
            xmlBody
        }
        Timber.w("WebDavFileSystemHandler: %s XML snippet: %s", reason, preview)
    }

    private fun extractRelativePath(href: String, requestedPath: String): String {
        val uriPath = try {
            val httpUrl = href.toHttpUrlOrNull()
            httpUrl?.encodedPath ?: href
        } catch (_: IllegalArgumentException) {
            href
        }
        val decodedPath = try {
            URLDecoder.decode(uriPath, "UTF-8")
        } catch (_: UnsupportedEncodingException) {
            uriPath
        }

        val baseHttpUrl = baseUrl.toHttpUrlOrNull()
        val baseDecodedPath = baseHttpUrl?.encodedPath?.let {
            try {
                URLDecoder.decode(it, "UTF-8")
            } catch (_: UnsupportedEncodingException) {
                it
            }
        } ?: ""

        var rel = if (baseDecodedPath.isNotEmpty() && baseDecodedPath != "/" && decodedPath.startsWith(baseDecodedPath)) {
            decodedPath.removePrefix(baseDecodedPath)
        } else {
            decodedPath
        }

        if (!decodedPath.startsWith("/") && requestedPath.isNotBlank()) {
            val cleanRequested = requestedPath.trim('/')
            rel = "$cleanRequested/$rel"
        }

        return rel.trim('/')
    }

    private fun parseHttpDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val formats = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        )
        return formats.firstNotNullOfOrNull { fmt ->
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(dateStr)?.time
            } catch (_: ParseException) {
                null
            }
        } ?: 0L
    }

    private data class MutableResponseProps(
        var href: String = "",
        var displayName: String = "",
        var contentLength: Long = 0L,
        var lastModified: Long = 0L,
        var isDirectory: Boolean = false,
    )

    companion object {
        private const val HTTP_CREATED = 201
        private const val HTTP_ACCEPTED = 202
        private const val HTTP_NO_CONTENT = 204
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_MULTI_STATUS = 207

        private const val HTTP_DEFAULT_PORT = 80
        private const val HTTPS_DEFAULT_PORT = 443
        private const val HTTPS_ALT_PORT = 8443

        private const val HTTP_PREFIX_LENGTH = 7
        private const val HTTPS_PREFIX_LENGTH = 8
        private const val LOG_PREVIEW_LIMIT = 500

        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

        private fun formatNetworkErrorMessage(e: IOException, targetUrl: String): String = when (e) {
            is SocketTimeoutException -> "Connection to $targetUrl timed out. Please check the IP address and network."
            is ConnectException -> "Failed to connect to $targetUrl. Please check if the server is running and IP/port are correct."
            is UnknownHostException -> "Host not found for $targetUrl. Please check the address."
            else -> "WebDAV operation failed: ${e.message ?: e.javaClass.simpleName}"
        }

        private fun buildBaseUrl(server: RemoteServer): String {
            var rawHost = server.host.trim()
            if (rawHost.isEmpty()) return "http://localhost/"

            val hasHttp = rawHost.startsWith("http://", ignoreCase = true)
            val hasHttps = rawHost.startsWith("https://", ignoreCase = true)

            val scheme = determineScheme(hasHttp, hasHttps, server.port)

            if (hasHttp) rawHost = rawHost.substring(HTTP_PREFIX_LENGTH)
            if (hasHttps) rawHost = rawHost.substring(HTTPS_PREFIX_LENGTH)

            val fullCandidate = "$scheme://$rawHost"
            val parsedUrl = fullCandidate.toHttpUrlOrNull()

            return if (parsedUrl != null) {
                formatParsedUrl(parsedUrl, rawHost, scheme, server.port)
            } else {
                formatFallbackUrl(rawHost, scheme, server.port)
            }
        }

        private fun determineScheme(hasHttp: Boolean, hasHttps: Boolean, port: Int): String = when {
            hasHttp -> "http"
            hasHttps -> "https"
            port == HTTPS_DEFAULT_PORT || port == HTTPS_ALT_PORT -> "https"
            else -> "http"
        }

        private fun formatParsedUrl(parsedUrl: HttpUrl, rawHost: String, scheme: String, port: Int): String {
            val hostPart = rawHost.substringBefore('/')
            val hasExplicitPortInHost = hostPart.contains(':')
            if (!hasExplicitPortInHost && port > 0) {
                val builder = parsedUrl.newBuilder()
                val isNonStandardPort = (scheme == "http" && port != HTTP_DEFAULT_PORT) ||
                    (scheme == "https" && port != HTTPS_DEFAULT_PORT)
                if (isNonStandardPort) {
                    builder.port(port)
                }
                return builder.build().toString().trimEnd('/') + "/"
            }
            return parsedUrl.toString().trimEnd('/') + "/"
        }

        private fun formatFallbackUrl(rawHost: String, scheme: String, port: Int): String {
            val cleanHost = rawHost.trimEnd('/')
            val isStandardOrExplicitPort = (scheme == "http" && port == HTTP_DEFAULT_PORT) ||
                (scheme == "https" && port == HTTPS_DEFAULT_PORT) || cleanHost.contains(':')
            val portString = if (isStandardOrExplicitPort) "" else ":$port"
            return "$scheme://$cleanHost$portString/"
        }
    }
}
