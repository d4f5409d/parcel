// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DhlDeliveryService : DeliveryService {
    override val nameResource: Int = R.string.service_dhl
    override val acceptsPostCode: Boolean = false
    override val requiresPostCode: Boolean = false

    override fun acceptsFormat(trackingId: String): Boolean {
        val dhlParcelFormat = """^(?:JJD|JVGL|3S|JV|JD)\d*$""".toRegex()
        return digits11Format.accepts(trackingId) || digits12Format.accepts(trackingId) || digits18Format.accepts(
            trackingId
        ) || emsFormat.accepts(trackingId) || dhlParcelFormat.accepts(trackingId)
    }

    override suspend fun getParcel(trackingId: String, postalCode: String?): Parcel {
        val resp = try {
            service.getShipments(trackingId)
        } catch (_: Exception) {
            throw ParcelNonExistentException()
        }

        val shipment = resp.shipments.first()

        val status = when (shipment.status.statusCode) {
            "unknown" -> Status.Unknown
            "pre-transit" -> Status.Preadvice
            "transit" -> when (shipment.status.status) {
                // DHL uses a "transit" status code for different statues
                "447" -> Status.Customs // ARRIVED AT CUSTOMS
                "506" -> Status.Customs // HELD AT CUSTOMS
                "449" -> Status.Customs // CLEARED CUSTOMS
                "576" -> Status.InWarehouse // PROCESSED AT LOCAL DISTRIBUTION CENTER
                "577" -> Status.OutForDelivery // DEPARTED FROM LOCAL DISTRIBUTION CENTER
                "OUT FOR DELIVERY" -> Status.OutForDelivery
                else -> Status.InTransit
            }

            "failure" -> when (shipment.status.status) {
                "103" -> Status.InWarehouse // Shipment is on hold
                else -> Status.DeliveryFailure
            }

            "delivered" -> Status.Delivered
            else -> logUnknownStatus("DHL", shipment.status.statusCode)
        }

        val history = shipment.events.map {
            ParcelHistoryItem(
                it.description ?: it.status,
                LocalDateTime.parse(it.timestamp, DateTimeFormatter.ISO_DATE_TIME),
                if (it.location == null) "Unknown location"
                else if (it.location.address.postalCode != null) "${it.location.address.postalCode} ${it.location.address.addressLocality}"
                else it.location.address.addressLocality
            )
        }

        return Parcel(shipment.id, history, status)
    }

    // please don't take it for yourself, make one for free - https://developer.dhl.com/
    private const val API_KEY = "VzYp08GaeA2esfeCPBLtzHkLShfRTk28"

    private val retrofit = Retrofit.Builder().baseUrl("https://api-eu.dhl.com/").client(api_client)
        .addConverterFactory(api_factory).build()

    private val service = retrofit.create(API::class.java)

    private interface API {
        @GET("track/shipments")
        @Headers("DHL-API-Key: $API_KEY")
        suspend fun getShipments(@Query("trackingNumber") id: String): ShipmentsResponse
    }

    @JsonClass(generateAdapter = true)
    internal data class ShipmentsResponse(
        val shipments: List<Shipment>,
    )

    @JsonClass(generateAdapter = true)
    internal data class Shipment(
        val id: String,
        val service: String,
        val events: List<Event>,
        val status: Event,
    )

    @JsonClass(generateAdapter = true)
    internal data class Event(
        val description: String?,
        val location: EventLocation?,
        val status: String,
        val statusCode: String,
        val timestamp: String,
    )

    @JsonClass(generateAdapter = true)
    internal data class EventLocation(
        val address: Address,
    )

    @JsonClass(generateAdapter = true)
    internal data class Address(
        val addressLocality: String,
        val postalCode: String?,
    )
}
