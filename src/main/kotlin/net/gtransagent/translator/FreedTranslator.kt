package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.FullBatchTranslator
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FreedTranslator : FullBatchTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    companion object {
        const val NAME = "Freed"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "freed"
            name = NAME
        })

        private val SUPPORTED_LANGUAGE_CODES = listOf(
            "ace",
            "af",
            "am",
            "an",
            "ar",
            "as",
            "ay",
            "az",
            "ba",
            "be",
            "bg",
            "bho",
            "bn",
            "br",
            "bs",
            "ca",
            "ceb",
            "ckb",
            "cs",
            "cy",
            "da",
            "de",
            "el",
            "en-GB",
            "en-US",
            "eo",
            "es",
            "es-419",
            "et",
            "eu",
            "fa",
            "fi",
            "fr",
            "ga",
            "gl",
            "gn",
            "gom",
            "gu",
            "ha",
            "he",
            "hi",
            "hr",
            "ht",
            "hu",
            "hy",
            "id",
            "ig",
            "is",
            "it",
            "ja",
            "jv",
            "ka",
            "kk",
            "kmr",
            "ko",
            "ky",
            "la",
            "lb",
            "lmo",
            "ln",
            "lt",
            "lv",
            "mai",
            "mg",
            "mi",
            "mk",
            "ml",
            "mn",
            "mr",
            "ms",
            "mt",
            "my",
            "nb",
            "ne",
            "nl",
            "oc",
            "om",
            "pa",
            "pag",
            "pam",
            "pl",
            "prs",
            "ps",
            "pt-BR",
            "pt-PT",
            "qu",
            "ro",
            "ru",
            "sa",
            "scn",
            "si",
            "sk",
            "sl",
            "sq",
            "sr",
            "st",
            "su",
            "sv",
            "sw",
            "ta",
            "te",
            "tg",
            "th",
            "tk",
            "tl",
            "tn",
            "tr",
            "ts",
            "tt",
            "uk",
            "ur",
            "uz",
            "vi",
            "wo",
            "xh",
            "yi",
            "yue",
            "zh-Hans",
            "zh-Hant",
            "zu"
        )
    }

    private val normalizedCodeToCanonical: Map<String, String> =
        SUPPORTED_LANGUAGE_CODES.associateBy { normalizeLangCode(it) }

    private lateinit var url: String
    private lateinit var languageModel: String
    private lateinit var usageType: String
    private lateinit var acceptLanguage: String
    private lateinit var appOsVersion: String
    private lateinit var appDevice: String
    private lateinit var appBuild: String
    private lateinit var appVersion: String
    private lateinit var userAgent: String
    private var retryCount: Int = 0
    private var retryDelayMs: Long = 0

    override fun initOkHttpClient(): OkHttpClient {
        val logInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }
        logInterceptor.redactHeader("apikey")
        logInterceptor.redactHeader("Authorization")
        logInterceptor.level = HttpLoggingInterceptor.Level.BASIC

        val cookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        }

        return OkHttpClient.Builder().connectTimeout(120, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).connectionPool(
                ConnectionPool(
                    getConcurrent(), 60L * 60, TimeUnit.SECONDS
                )
            ).pingInterval(30, TimeUnit.SECONDS).retryOnConnectionFailure(true).cookieJar(cookieJar)
            .addNetworkInterceptor(logInterceptor).build()
    }

    override fun getName(): String {
        return NAME
    }

    override fun isSupported(srcLanguage: String, targetLanguage: String): Boolean {
        return toFreedLangOrNull(srcLanguage) != null && toFreedLangOrNull(targetLanguage) != null
    }

    override fun isSupported(targetLanguage: String): Boolean {
        return toFreedLangOrNull(targetLanguage) != null
    }

    override fun getSupportedEngines(): List<PublicConfig.TranslateEngine> {
        return supportedEngines
    }

    override fun init(configs: Map<*, *>): Boolean {
        url = configs["url"] as String
        languageModel = configs["languageModel"] as String
        usageType = configs["usageType"] as String
        acceptLanguage = configs["acceptLanguage"] as String
        appOsVersion = configs["appOsVersion"] as String
        appDevice = configs["appDevice"] as String
        appBuild = configs["appBuild"] as String
        appVersion = configs["appVersion"] as String
        userAgent = configs["userAgent"] as String
        retryCount = (configs["retryCount"] as Number).toInt()
        retryDelayMs = (configs["retryDelayMs"] as Number).toLong()

        logger.info("FreedTranslator init success, supportedEngines: $supportedEngines")
        return true
    }

    private fun normalizeLangCode(language: String): String {
        return language.trim().replace('_', '-').lowercase()
    }

    private fun toFreedLangOrNull(language: String): String? {
        if (language.isBlank()) {
            return null
        }
        val normalized = normalizeLangCode(language)
        when (normalized) {
            "auto" -> return null
            "zh", "zh-cn", "zh-hans" -> return "zh-Hans"
            "zh-tw", "zh-hant" -> return "zh-Hant"
            "en" -> return "en-US"
            "pt" -> return "pt-PT"
        }

        normalizedCodeToCanonical[normalized]?.let { return it }

        val base = normalizeLangCode(CommonUtils.getLang(language))
        return normalizedCodeToCanonical[base]
    }

    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        targetLang: String,
        inputs: List<String>,
        sourceLang: String?,
        isSourceLanguageUserSetToAuto: Boolean, // true if user selects "auto" as the source language
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        try {
            logger.debug("Freed translateTexts start, target:${targetLang}, inputs:${inputs}")
            val begin = System.currentTimeMillis()

            val targetCode = toFreedLangOrNull(targetLang)
                ?: throw Status.UNIMPLEMENTED.withDescription("Freed translate failed, targetLang:$targetLang is not supported")
                    .asRuntimeException()
            val sourceCode = if (isSourceLanguageUserSetToAuto) {
                null
            } else {
                toFreedLangOrNull(sourceLang ?: "")
            }
            if (!isSourceLanguageUserSetToAuto && sourceCode == null) {
                throw Status.UNIMPLEMENTED.withDescription("Freed translate failed, sourceLang:$sourceLang is not supported")
                    .asRuntimeException()
            }

            val jsonMap = mutableMapOf<String, Any>(
                Pair("text", inputs),
                Pair("target_lang", targetCode),
                Pair("language_model", languageModel),
                Pair("usage_type", usageType)
            )
            if (!sourceCode.isNullOrBlank()) {
                jsonMap["source_lang"] = sourceCode
            }

            val payload = gson.toJson(jsonMap)
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            return executeWithRetry { executeOnce(body, begin, targetLang, inputs) }
        } catch (e: Exception) {
            logger.warn(
                "Freed translation failure, inputs:${inputs}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }

    private fun executeWithRetry(block: () -> List<String>): List<String> {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                if (attempt >= retryCount) {
                    throw e
                }
                attempt += 1
                Thread.sleep(retryDelayMs)
            }
        }
    }

    private fun executeOnce(
        body: okhttp3.RequestBody,
        begin: Long,
        targetLang: String,
        inputs: List<String>
    ): List<String> {
        val request = Request.Builder().url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "*/*")
            .addHeader("Connection", "keep-alive")
            .addHeader("x-app-os-version", appOsVersion)
            .addHeader("x-app-device", appDevice)
            .addHeader("User-Agent", userAgent)
            .addHeader("x-app-build", appBuild)
            .addHeader("x-app-version", appVersion)
            .addHeader("Accept-Language", acceptLanguage)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (it.isSuccessful && Objects.nonNull(it.body)) {
                val end = System.currentTimeMillis()
                val result = it.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                val translations = map["translations"] as? List<Map<*, *>>
                if (translations == null) {
                    logger.error("Freed translation return invalid, inputs:${inputs}, target:${targetLang}, result:${result}")
                    throw Status.UNAVAILABLE.withDescription("Freed translation return invalid").asRuntimeException()
                }
                val outputs = translations.map { item -> item["text"]?.toString() ?: "" }
                logger.info("Freed translateTexts end, time:${end - begin}, results:${outputs}, inputs:${inputs}, target:${targetLang}")
                return outputs
            } else {
                logger.error("Freed translation return code invalid, inputs:${inputs}, target:${targetLang}, code:${it.code}")
                throw Status.UNAVAILABLE.withDescription("Freed return code invalid, code:${it.code}")
                    .asRuntimeException()
            }
        }
    }
}
