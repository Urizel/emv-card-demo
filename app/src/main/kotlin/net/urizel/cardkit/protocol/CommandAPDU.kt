@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)

package net.urizel.cardkit.protocol

import io.github.binaryfoo.tlv.BerTlv
import io.github.binaryfoo.tlv.Tag

data class CommandAPDU(
    val command: CommandHeader,
    val body: CommandBody? = null,
    val le: UByte
) : ByteData {

    override val byteData: UByteArray by lazy {
        command.byteData + (body?.byteData ?: ubyteArrayOf()) + le
    }

    companion object {

        fun selectPSE(name: String): CommandAPDU {
            val data = name.encodeToByteArray().toUByteArray()
            return CommandAPDU(
                CommandHeader(
                    CommandId.SELECT,
                    0b00000100u,
                    0b00000000u,
                ),
                CommandBody(data),
                le = 0u
            )
        }

        fun selectAID(aid: UByteArray): CommandAPDU {
            return CommandAPDU(
                CommandHeader(
                    CommandId.SELECT,
                    0b00000100u,
                    0b00000000u,
                ),
                CommandBody(aid),
                le = 0u
            )
        }

        fun getProcessingOptions(): CommandAPDU {
            return CommandAPDU(
                CommandHeader(
                    CommandId.GET_PROCESSING_OPTIONS,
                    0b00000000u,
                    0b00000000u,
                ),
                CommandBody(
                    BerTlv.newInstance(CustomTags.COMMAND_TEMPLATE, byteArrayOf())
                        .toBinary().toUByteArray()
                ),
                le = 0u
            )
        }

        fun readRecord(sfi: UByte, recordIdx: UByte): CommandAPDU {
            return CommandAPDU(
                CommandHeader(
                    CommandId.READ_RECORD,
                    recordIdx,
                    ((sfi.toUInt() shl 3) or 0b00000100u).toUByte(),
                ),
                le = 0x00u
            )
        }

        fun getData(tag: Tag): CommandAPDU {
            val tagValue = tag.byteArray
            return CommandAPDU(
                CommandHeader(
                    CommandId.GET_DATA,
                    tagValue[0].toUByte(),
                    tagValue[1].toUByte(),
                ),
                le = 0x00u
            )
        }

        fun generateAc(filledDol: UByteArray): CommandAPDU {
            return CommandAPDU(
                CommandHeader(
                    CommandId.GENERATE_AC,
                    0b01000000u,
                    0x00u,
                ),
                CommandBody(filledDol),
                le = 0x00u
            )
        }

        fun getChallenge(): CommandAPDU {
            return CommandAPDU(
                CommandHeader(
                    CommandId.GET_CHALLENGE,
                    0x00u,
                    0x00u
                ),
                le = 0x00u
            )
        }
    }
}

enum class CommandId(
    val cla: UByte,
    val ins: UByte
) {
    SELECT(0x00u, 0xA4u),
    READ_RECORD(0x00u, 0xB2u),
    GET_PROCESSING_OPTIONS(0x80u, 0xA8u),
    GENERATE_AC(0x80u, 0xAEu),
    GET_DATA(0x80u, 0xCAu),
    GET_CHALLENGE(0x00u, 0x84u),
}

data class CommandHeader(
    val command: CommandId,
    val p1: UByte,
    val p2: UByte
) : ByteData {
    override val byteData: UByteArray = ubyteArrayOf(
        command.cla,
        command.ins,
        p1,
        p2
    )

    override fun toString(): String {
        return "CommandHeader(command=${command}, p1=0x${p1.toHexString(HexFormat.UpperCase)}, " +
                "p2=0x${p1.toHexString(HexFormat.UpperCase)})"
    }
}

data class CommandBody(
    val data: UByteArray
) : ByteData {
    override val byteData: UByteArray by lazy {
        val uSize = data.size.toUInt()
        when {
            uSize == 0u -> ubyteArrayOf()
            uSize <= UByte.MAX_VALUE.toUInt() -> ubyteArrayOf(
                uSize.toUByte()
            ) + data

            uSize <= UShort.MAX_VALUE.toUInt() -> ubyteArrayOf(
                0x00u,
                (uSize shr 0).toUByte(),
                (uSize shr 8).toUByte()
            ) + data

            else -> throw IllegalArgumentException("Body size ${data.size} is larger than ${UShort.MAX_VALUE}")
        }
    }

    override fun toString(): String {
        return "CommandBody(data=[${byteData.toHexString(HexFormat.UpperCase)}])"
    }
}

interface ByteData {
    val byteData: UByteArray
}
