// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.os.LocaleList
import android.text.Html
import android.util.Log
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Reverse-engineered from their private API. Pretty basic at least

object GLSDeliveryService : DeliveryService {
    override val nameResource: Int = R.string.service_gls
    override val acceptsPostCode: Boolean = true
    override val requiresPostCode: Boolean = true

    override suspend fun getParcel(trackingId: String, postalCode: String?): Parcel {
        val locale = LocaleList.getDefault().get(0).language

        val resp = try {
            service.getExtendedParcel(
                id = trackingId,
                postalCode = postalCode!!,
                locale = locale
            )
        } catch (e: Exception) {
            Log.d("GLSDeliveryService", "exception", e)
            throw ParcelNonExistentException()
        }

        val history = resp.history.map { item ->
            ParcelHistoryItem(
                Html.fromHtml(item.evtDscr, Html.FROM_HTML_MODE_LEGACY).toString(),
                LocalDateTime.parse("${item.date}T${item.time}", DateTimeFormatter.ISO_DATE_TIME),
                if (item.address.city != "")
                    "${item.address.city}, ${item.address.countryName}"
                else item.address.countryName
            )
        }

        val status = when (resp.progressBar.statusInfo) {
            "PREADVICE" -> Status.Preadvice
            "INTRANSIT" -> Status.InTransit
            "INWAREHOUSE" -> Status.InWarehouse
            "INDELIVERY" -> Status.OutForDelivery
            "DELIVERED" -> Status.Delivered
            else -> logUnknownStatus("GLS", resp.progressBar.statusInfo)
        }

        val properties = mutableMapOf<Int, String>()

        resp.infos.forEach {
            if (it.type == "WEIGHT") {
                properties[R.string.property_weight] = it.value
            }
        }

        if (resp.arrivalTime != null) {
            val type =
                if (status == Status.Delivered) R.string.property_delivery_time
                else R.string.property_eta
            properties[type] = resp.arrivalTime.value
        }

        val parcel = Parcel(trackingId, history, status, properties)
        return parcel
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://gls-group.com/app/service/open/rest/GROUP/")
        .client(api_client)
        .addConverterFactory(api_factory)
        .build()
    private val service = retrofit.create(API::class.java)

    private interface API {
        @GET("{locale}/rstt028/{id}")
        suspend fun getExtendedParcel(
            @Path("locale") locale: String,
            @Path("id") id: String,
            @Query("postalCode") postalCode: String,
        ): ExtendedParcelInfo
    }

    @JsonClass(generateAdapter = true)
    internal data class ExtendedParcelInfo(
        val history: List<GLSHistoryItem>,
        val progressBar: Progress,
        val infos: List<GLSTypedProperty>,
        val references: List<GLSTypedProperty>,
        val arrivalTime: GLSProperty?,
    )

    @JsonClass(generateAdapter = true)
    internal data class GLSTypedProperty(
        val type: String,
        val name: String,
        val value: String,
    )

    @JsonClass(generateAdapter = true)
    internal data class GLSProperty(
        val name: String,
        val value: String,
    )

    @JsonClass(generateAdapter = true)
    internal data class GLSHistoryItem(
        val time: String,
        val date: String,
        val evtDscr: String,
        val address: HistoryAddress,
    )

    @JsonClass(generateAdapter = true)
    internal data class HistoryAddress(
        val city: String,
        val countryName: String,
        val countryCode: String,
    )

    @JsonClass(generateAdapter = true)
    internal data class Progress(
        val statusInfo: String,
    )
}
