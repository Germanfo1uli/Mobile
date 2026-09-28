package com.example.labmob

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Headers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GoldRate(val rublesPerGram: Double, val date: String)

private interface CbrGoldApi {
    @Headers("User-Agent: AltHunt/1.0")
    @GET("scripts/xml_metall.asp")
    suspend fun metalRates(
        @Query(value = "date_req1", encoded = true) from: String,
        @Query(value = "date_req2", encoded = true) to: String,
    ): ResponseBody
}

private interface AlthuntGoldApi {
    @GET("gold-rate")
    suspend fun latestGold(): ResponseBody
}

class GoldRateRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()

    private val cbrApi = Retrofit.Builder()
        .baseUrl("https://www.cbr.ru/")
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(CbrGoldApi::class.java)

    private val backendApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(AlthuntGoldApi::class.java)

    suspend fun latest(): GoldRate = withContext(Dispatchers.IO) {
        runCatching { backendApi.latestGold().use { parseBackendGold(it.string()) } }
            .getOrElse { loadDirectlyFromCbr() }
    }

    private suspend fun loadDirectlyFromCbr(): GoldRate {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val to = formatter.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -14)
        val from = formatter.format(calendar.time)
        return cbrApi.metalRates(from, to).use { parseLatestGold(it.string()) }
    }

    internal fun parseBackendGold(json: String): GoldRate {
        val value = Regex(""""rublesPerGram"\s*:\s*([0-9]+(?:\.[0-9]+)?)""")
            .find(json)?.groupValues?.get(1)?.toDoubleOrNull()
        val date = Regex(""""date"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
        require(value != null && value > 0 && !date.isNullOrBlank()) { "Сервер не вернул актуальный курс золота" }
        return GoldRate(value, date)
    }

    internal fun parseLatestGold(xml: String): GoldRate {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        var latest: GoldRate? = null
        var recordDate = ""
        var isGold = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Record") {
                recordDate = parser.getAttributeValue(null, "Date").orEmpty()
                isGold = parser.getAttributeValue(null, "Code") == "1"
            } else if (event == XmlPullParser.START_TAG && parser.name == "Buy" && isGold) {
                val value = parser.nextText().trim().replace(',', '.').toDoubleOrNull()
                if (value != null) latest = GoldRate(value, recordDate)
            } else if (event == XmlPullParser.END_TAG && parser.name == "Record") {
                isGold = false
            }
            event = parser.next()
        }
        return requireNotNull(latest) { "ЦБ не вернул актуальный курс золота" }
    }
}
