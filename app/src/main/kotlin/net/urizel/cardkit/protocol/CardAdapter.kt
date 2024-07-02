@file:OptIn(
    ExperimentalUnsignedTypes::class,
    ExperimentalStdlibApi::class,
)

package net.urizel.cardkit.protocol

import android.nfc.tech.IsoDep
import io.github.binaryfoo.EmvTags
import io.github.binaryfoo.decoders.DOLParser
import io.github.binaryfoo.tlv.BerTlv
import io.github.binaryfoo.tlv.ConstructedBerTlv
import io.github.binaryfoo.tlv.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber
import java.util.Currency

private typealias EventBus = MutableSharedFlow<Event>

sealed interface Event

data object ClearEvent : Event

data class CommandEvent(
    val command: CommandAPDU
) : Event

data class ResponseEvent(
    val responseAPDU: ResponseAPDU
) : Event

data class LogEvent(
    val description: String,
    val exception: Exception? = null
) : Event

data class AflEvent(
    val afl: List<AFL>
) : Event

private fun EventBus.log(message: String) {
    this.tryEmit(LogEvent(message))
}

private fun EventBus.command(command: CommandAPDU) {
    this.tryEmit(CommandEvent(command))
}

private fun EventBus.response(response: ResponseAPDU) {
    this.tryEmit(ResponseEvent(response))
}

private fun EventBus.afl(afl: List<AFL>) {
    this.tryEmit(AflEvent(afl))
}

private fun EventBus.clear() {
    this.tryEmit(ClearEvent)
}

interface CardAdapter {
    val events: Flow<Event>
    fun process(isoDep: IsoDep)
}

class CardAdapterImpl : CardAdapter {

    private val bus = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    override val events: Flow<Event> = bus

    override fun process(isoDep: IsoDep): Unit = isoDep.use {
        if (!isoDep.isConnected) {
            isoDep.connect()
        }

        bus.clear()

        val selectPse = CommandAPDU.selectPSE("2PAY.SYS.DDF01")
        val selectResult = execute(isoDep, selectPse)
        if (!selectResult.isSuccess()) {
            return@use
        }

        val applicationTemplate = selectResult.dataAsTlv
            ?.findTlv(EmvTags.FCI_PROPRIETARY_TEMPLATE)
            ?.findTlv(EmvTags.FCI_DISCRETIONARY_DATA)
            ?.findTlv(CustomTags.APPLICATION_TEMPLATE)
            ?: return@use

        val appId = applicationTemplate.findTlv(CustomTags.APPLICATION_IDENTIFIER) ?: return@use

        val selectApp = CommandAPDU.selectAID(appId.getValue().toUByteArray())
        val selectAppResult = execute(isoDep, selectApp)
        if (!selectAppResult.isSuccess()) {
            return@use
        }

        val getProcessingOptions = CommandAPDU.getProcessingOptions()
        val gpoResponse = execute(isoDep, getProcessingOptions).data

        val afl = BerTlv.parse(gpoResponse.toByteArray())
            .findTlv(EmvTags.AFL)
            ?.getValue()
            ?.toUByteArray()
            ?: return@use

        val filesToRead = AFL.parseList(afl)
        bus.afl(filesToRead)

        val records = filesToRead.flatMap { file ->
            (file.firstRecordIdx..file.lastRecordIdx)
                .mapNotNull { recordIdx ->
                    val cmd = CommandAPDU.readRecord(file.sfi, recordIdx.toUByte())
                    execute(isoDep, cmd).dataAsTlv
                }
        }

        val getChallenge = CommandAPDU.getChallenge()
        val challenge = execute(isoDep, getChallenge)

        val cdol = records.asSequence()
            .mapNotNull { it.findTlv(EmvTags.CDOL_1) }
            .firstOrNull() ?: return@use

        val parsedCdol = DOLParser().parse(cdol.getValue())
        val dolFiller = CDOLDataProvider(challenge.data)

        val filledDol = parsedCdol
            .map { dolElement ->
                dolFiller.getDataForDol(dolElement.tag, dolElement.length)
            }
            .flatten()
            .toUByteArray()

        val generateAc = CommandAPDU.generateAc(filledDol)
        execute(isoDep, generateAc)
    }

    private fun execute(isoDep: IsoDep, command: CommandAPDU): ResponseAPDU {
        bus.command(command)
        val commandData = command.byteData
        val response = isoDep.transceive(commandData.toByteArray()).toUByteArray()
        Timber.d("Response raw data:\n ${response.hex()}")
        val responseAPDU = ResponseAPDU.parse(response)
        Timber.d("Response:\n $responseAPDU")
        responseAPDU.dataAsTlv?.let {
            Timber.d("Decoded tlv data:\n ${it.prettyPrint()}")
        }

        bus.response(responseAPDU)

        return responseAPDU
    }

    private fun UByteArray.hex() =
        this.toHexString(HexFormat.UpperCase).chunked(2).joinToString(separator = " ")

    private fun UShort.hex() =
        this.toHexString(HexFormat.UpperCase).chunked(2).joinToString(separator = " ")
}

// FIXME horribly inefficient
fun BerTlv.prettyPrint(): String {
    val result = StringBuilder()
    val name = EmvTags.METADATA.get(this.tag).fullName

    result.append("${this.tag.hexString} ($name)")

    if (this is ConstructedBerTlv) {
        for (child in this.getChildren()) {
            result.append("\n").append(
                child.prettyPrint().prependIndent("\t")
            )
        }
    } else {
        result.append("\n\t").append(this.valueAsHexString)

        // Special handling for some primitives
        val textValue = when (this.tag) {
            EmvTags.APPLICATION_CURRENCY_CODE -> {
                val currencyNumber = this.valueAsHexString.toInt(16)
                val currency = Currency.getAvailableCurrencies()
                    .firstOrNull { it.numericCode == currencyNumber }
                currency?.currencyCode ?: currencyNumber
            }

            else -> String(this.getValue())
        }
        result.append("\n\t").append(textValue)
    }

    return result.toString()
}

object CustomTags {
    val APPLICATION_TEMPLATE = Tag.fromHex("61")
    val APPLICATION_IDENTIFIER = Tag.fromHex("4F")
    val COMMAND_TEMPLATE = Tag.fromHex("83")
}
