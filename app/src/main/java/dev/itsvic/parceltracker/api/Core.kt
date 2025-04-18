// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.util.Log
import com.squareup.moshi.Moshi
import dev.itsvic.parceltracker.BuildConfig
import dev.itsvic.parceltracker.R
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDateTime

enum class Service {
    UNDEFINED,
    EXAMPLE,

    // International
    DHL,
    GLS,
    UPS,

    // United Kingdom
    DPD_UK,
    EVRI,

    // Europe
    BELPOST,
    MAGYAR_POSTA,
    PACKETA,
    POLISH_POST,
    POSTE_ITALIANE,
    SAMEDAY_BG,
    SAMEDAY_HU,
    SAMEDAY_RO,

    // Asia
    SPX_TH,
}

val serviceOptions =
    Service.entries.filter { return@filter it != Service.UNDEFINED && it != Service.EXAMPLE }
        .toList()

fun getDeliveryService(service: Service): DeliveryService? {
    return when (service) {
        Service.DHL -> DhlDeliveryService
        Service.GLS -> GLSDeliveryService
        Service.UPS -> UPSDeliveryService

        Service.DPD_UK -> DpdUkDeliveryService
        Service.EVRI -> EvriDeliveryService

        Service.BELPOST -> BelpostDeliveryService
        Service.MAGYAR_POSTA -> MagyarPostaDeliveryService
        Service.PACKETA -> PacketaDeliveryService
        Service.POLISH_POST -> PolishPostDeliveryService
        Service.POSTE_ITALIANE -> PosteItalianeDeliveryService
        Service.SAMEDAY_BG -> SamedayBulgariaDeliveryService
        Service.SAMEDAY_HU -> SamedayHungaryDeliveryService
        Service.SAMEDAY_RO -> SamedayRomaniaDeliveryService

        Service.SPX_TH -> SPXThailandDeliveryService

        Service.EXAMPLE -> ExampleDeliveryService
        else -> null
    }
}

internal val api_client = OkHttpClient.Builder()
    .addInterceptor(
        HttpLoggingInterceptor {
            Log.d("OkHttp", it)
        }.setLevel(
            if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.BASIC
        )
    )
    .build()

internal val api_moshi: Moshi = Moshi.Builder().build()
internal val api_factory = MoshiConverterFactory.create(api_moshi)

// TODO: fill out with more data
data class Parcel(
    val id: String,
    val history: List<ParcelHistoryItem>,
    val currentStatus: Status,
    val properties: Map<Int, String> = mapOf(),
)

data class ParcelHistoryItem(
    val description: String,
    val time: LocalDateTime,
    val location: String,
)

enum class Status(val nameResource: Int) {
    Preadvice(R.string.status_preadvice),
    InTransit(R.string.status_in_transit),
    InWarehouse(R.string.status_in_warehouse),
    Customs(R.string.status_customs),
    OutForDelivery(R.string.status_out_for_delivery),
    DeliveryFailure(R.string.status_delivery_failure),
    Delivered(R.string.status_delivered),
    AwaitingPickup(R.string.status_awaiting_pickup),
    PickedUp(R.string.status_picked_up),
    Unknown(R.string.status_unknown),
    NetworkFailure(R.string.status_network_failure),
    NoData(R.string.status_no_data),
}

suspend fun getParcel(id: String, postCode: String?, service: Service): Parcel {
    // use DeliveryService abstraction if possible, otherwise default to the old hardcoded list
    getDeliveryService(service)?.let {
        return it.getParcel(id, postCode)
    }

    throw NotImplementedError("Service $service has no DeliveryService object")
}

fun getDeliveryServiceName(service: Service): Int? {
    return getDeliveryService(service)?.nameResource
}

interface DeliveryService {
    val nameResource: Int
    val acceptsPostCode: Boolean
    val requiresPostCode: Boolean
    suspend fun getParcel(trackingId: String, postalCode: String?): Parcel
    fun acceptsFormat(trackingId: String): Boolean {
        return false
    }
}

class ParcelNonExistentException : Exception("Parcel does not exist in delivery service API")

internal fun logUnknownStatus(service: String, data: String): Status {
    Log.d("APICore", "Unknown status reported by $service: $data")
    return Status.Unknown
}
