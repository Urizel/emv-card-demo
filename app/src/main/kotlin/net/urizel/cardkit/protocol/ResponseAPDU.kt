@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)

package net.urizel.cardkit.protocol

import io.github.binaryfoo.tlv.BerTlv
import timber.log.Timber

data class ResponseAPDU(
    val data: UByteArray,
    val status: UShort
) {
    val size get() = data.size

    val dataAsTlv: BerTlv? by lazy {
        try {
            BerTlv.parse(data.toByteArray())
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }
    
    fun isSuccess() = status == SW_OK

    override fun toString(): String {
        return "ResponseAPDU(data=${data.toHexString(HexFormat.UpperCase)}, status=0x${status.toHexString(HexFormat.UpperCase)}))"
    }

    companion object {
        fun parse(response: UByteArray): ResponseAPDU {
            val status = (response[response.lastIndex - 1].toUInt() shl 8) + response[response.lastIndex]
            val data = if (response.size > 2) {
                response.sliceArray(0..(response.lastIndex - 2))
            } else {
                ubyteArrayOf()
            }
            return ResponseAPDU(
                data,
                status.toUShort()
            )
        }

        const val SW_OK: UShort = 0x9000u
    }
}
