package net.urizel.cardkit.protocol

data class AFL(
    val sfi: UByte,
    val firstRecordIdx: UByte,
    val lastRecordIdx: UByte,
) {
    companion object {
        fun parse(data: UByteArray): AFL {
            return AFL(
                sfi = (data[0].toUInt() shr 3).toUByte(),
                firstRecordIdx = data[1],
                lastRecordIdx = data[2],
            )
        }

        fun parseList(data: UByteArray): List<AFL> {
            return data.chunked(4) { parse(it.toUByteArray()) }
        }
    }
}
