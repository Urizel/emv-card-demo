@file:OptIn(
    ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class,
    ExperimentalMaterial3Api::class
)

package net.urizel.cardkit.ui

import android.nfc.tech.IsoDep
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.binaryfoo.EmvTags
import io.github.binaryfoo.tlv.BerTlv
import io.github.binaryfoo.tlv.ConstructedBerTlv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import net.urizel.cardkit.protocol.AflEvent
import net.urizel.cardkit.protocol.CardAdapter
import net.urizel.cardkit.protocol.ClearEvent
import net.urizel.cardkit.protocol.CommandEvent
import net.urizel.cardkit.protocol.Event
import net.urizel.cardkit.protocol.LogEvent
import net.urizel.cardkit.R
import net.urizel.cardkit.protocol.ResponseEvent
import net.urizel.cardkit.protocol.AFL
import net.urizel.cardkit.protocol.CommandAPDU
import net.urizel.cardkit.protocol.CommandBody
import net.urizel.cardkit.protocol.CommandHeader
import net.urizel.cardkit.protocol.CommandId
import net.urizel.cardkit.ui.theme.CardReaderTheme
import java.util.Currency

@Composable
fun MainContent(viewModel: MainViewModel) {
    CardReaderTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    colors = topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(stringResource(id = R.string.app_name))
                    }
                )
            },
        ) { innerPadding ->
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            EventList(
                modifier = Modifier.padding(innerPadding),
                events = state.events
            )
        }
    }
}

@Composable
private fun EventList(
    modifier: Modifier,
    events: List<Event>
) {
    LazyColumn(modifier) {
        events.forEach { event ->
            item {
                when (event) {
                    is LogEvent -> LogItem(event = event)
                    is CommandEvent -> CommandItem(event = event)
                    is ResponseEvent -> ResponseItem(event = event)
                    is AflEvent -> AflItem(event = event)
                    is ClearEvent -> throw IllegalArgumentException("Forbidden event type: $event")
                }

            }
        }
    }
}

// -- Event items

@Composable
private fun LogItem(event: LogEvent) = Item {
    Text(text = event.description)
}

@Composable
private fun CommandItem(event: CommandEvent) = CollapsibleItem(
    collapsedContent = {
        Text(text = event.command.command.command.name)
    },
    expandedContent = {
        val command = event.command
        Column {
            TableRow(name = "CLA", value = command.command.command.cla.toHexString())
            TableRow(name = "INS", value = command.command.command.ins.toHexString())
            TableRow(name = "P1", value = command.command.p1.toHexString())
            TableRow(name = "P2", value = command.command.p2.toHexString())
            val data = command.body?.data
            TableRow(name = "Data", value = data?.toHexString() ?: "null")
            if (data != null) {
                TableRow(name = "", value = String(data.toByteArray()))
            }
        }
    }
)

@Composable
private fun ResponseItem(event: ResponseEvent) = CollapsibleItem(
    collapsedContent = {
        Text(
            text = "Status code 0x${event.responseAPDU.status.toHexString().uppercase()}, " +
                    "size: ${event.responseAPDU.data.size} bytes"
        )
    },
    expandedContent = {
        val responseAPDU = event.responseAPDU
        val tlv = responseAPDU.dataAsTlv
        if (tlv != null) {
            TlvRow(tlv = tlv)
        } else {
            Text(
                text = responseAPDU.data.toHexString().split()
            )
        }
    }
)

@Composable
private fun AflItem(event: AflEvent) = CollapsibleItem(
    collapsedContent = {
        Text(text = "Found ${event.afl.size} AFL records")
    },
    expandedContent = {
        Column {
            event.afl.forEach { afl ->
                TableRow(
                    name = afl.sfi.toHexString(),
                    value = "${afl.firstRecordIdx.toHexString()}:${afl.lastRecordIdx.toHexString()}"
                )
            }
        }
    }
)

// -- Common items

@Composable
private fun Item(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            content()
        }
    }
}

@Composable
fun CollapsibleItem(
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable { expanded = !expanded }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                collapsedContent()

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { expanded = !expanded })
                {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                ) {
                    expandedContent()
                }
            }
        }
    }
}

// -- Parts

@Composable
private fun TableRow(
    name: String,
    value: String
) = Row(
    modifier = Modifier.fillMaxWidth()
) {
    Text(
        text = name,
        Modifier
            .weight(1f / 3)
            .padding(8.dp)
    )
    Text(
        text = value,
        Modifier
            .weight(2f / 3)
            .padding(8.dp)
    )
}

@Composable
private fun TlvRow(
    modifier: Modifier = Modifier,
    tlv: BerTlv,
): Unit = Column(
    modifier = modifier.fillMaxWidth()
) {
    val name = EmvTags.METADATA.get(tlv.tag).fullName
    Text(text = "${tlv.tag.hexString} ($name)")

    if (tlv is ConstructedBerTlv) {
        Column {
            tlv.getChildren().forEach { child: BerTlv ->
                val padding = Modifier.padding(start = 8.dp)
                TlvRow(
                    modifier = padding,
                    tlv = child
                )
            }
        }
    } else {
        val textValue = when (tlv.tag) {
            EmvTags.APPLICATION_CURRENCY_CODE -> {
                val currencyNumber = tlv.valueAsHexString.toInt(16)
                val currency = Currency.getAvailableCurrencies()
                    .firstOrNull { it.numericCode == currencyNumber }
                currency?.currencyCode ?: currencyNumber.toString()
            }

            else -> String(tlv.getValue())
        }

        Column(
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(text = tlv.valueAsHexString.split())
            Text(text = textValue)
        }

    }
}

private fun String.split(): String = this
    .uppercase()
    .chunked(16)
    .joinToString(separator = "\n")

// -- Preview

private val testEvents = listOf(
    LogEvent("Event 1"),
    CommandEvent(
        CommandAPDU(
            command = CommandHeader(
                command = CommandId.SELECT,
                p1 = 0x01u,
                p2 = 0x02u,
            ),
            body = CommandBody(
                ubyteArrayOf(0x0Au, 0x0Bu, 0x0Cu)
            ),
            le = 0x00u
        )
    ),
    AflEvent(
        afl = listOf(
            AFL(0x40u, 0x10u, 0x80u),
            AFL(0x80u, 0x10u, 0x80u),
        )
    ),
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val cardAdapter = object : CardAdapter {
        override val events: Flow<Event>
            get() = testEvents.asFlow()

        override fun process(isoDep: IsoDep) {
            // do nothing
        }

    }
    val viewModel = MainViewModel(cardAdapter)
    MainContent(viewModel = viewModel)
}
