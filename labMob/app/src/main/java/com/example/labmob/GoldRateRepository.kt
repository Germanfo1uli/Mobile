package com.example.labmob

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class GoldRate(val rublesPerGram: Double, val date: String)

private interface CbrGoldApi {
    @GET("scripts/xml_metall.asp")
    suspend fun metalRates(
        @Query("date_req1") from: String,
        @Query("date_req2") to: String,
    ): ResponseBody
}

class GoldRateRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://www.cbr.ru/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(CbrGoldApi::class.java)

    suspend fun latest(): GoldRate = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val to = formatter.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -14)
        val from = formatter.format(calendar.time)
        api.metalRates(from, to).use { parseLatestGold(it.string()) }
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
