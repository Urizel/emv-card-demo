@file:OptIn(ExperimentalUnsignedTypes::class)

package net.urizel.cardkit.protocol

import io.github.binaryfoo.EmvTags
import io.github.binaryfoo.tlv.Tag
import timber.log.Timber

class CDOLDataProvider(
    private val iccDynamicNumber: UByteArray
) {

    fun getDataForDol(tag: Tag, length: Int): UByteArray {
        val tagName = EmvTags.METADATA.get(tag).fullName
        Timber.i("DOL request for tag ${tag.hexString} ($tagName) of length $length")

        val value = if (tag == EmvTags.ICC_DYNAMIC_NUMBER) {
            iccDynamicNumber
        } else {
            DATA[tag]
        }

        return if (value?.size != length) {
            // Shouldn't generally happen, and usually means error.
            // But we don't care.
            UByteArray(length)
        } else value
    }

    companion object {
        private val DATA = mapOf(
            EmvTags.AMOUNT_AUTHORIZED to ubyteArrayOf(
                0x00u, 0x00u, 0x00u, 0x00u, 0x03u, 0x50u
            ),
            EmvTags.AMOUNT_OTHER to UByteArray(6),

            EmvTags.TERMINAL_COUNTRY_CODE to ubyteArrayOf(0x02u, 0x76u),

            EmvTags.TERMINAL_CURRENCY_CODE to ubyteArrayOf(
                0x08u,
                0x26u
            ),

            EmvTags.TERMINAL_VERIFICATION_RESULTS to ubyteArrayOf(
                0b00000010u,
                0b00000000u,
                0b00000000u,
                0b00000010u,
            ),

            EmvTags.TRANSACTION_DATE to ubyteArrayOf(
                0x24u,
                0x07u,
                0x02u,
            ),

            EmvTags.TRANSACTION_TYPE to ubyteArrayOf(0x00u), // POS

            // Chosen by fair dice roll
            // Guaranteed to be random
            EmvTags.UNPREDICTABLE_NUMBER to UByteArray(4) { 0x04u },

            EmvTags.TERMINAL_TYPE to ubyteArrayOf(0x13u), // Attended, offline only

            EmvTags.DATA_AUTHENTICATION_CODE to ubyteArrayOf(0x13u, 0x37u),

            EmvTags.CVM_RESULTS to ubyteArrayOf(0x1fu, 0x00u, 0x02u), // all good

            EmvTags.TRANSACTION_TIME to ubyteArrayOf(0x00u, 0x00u, 0x00u),
            )
    }
}
