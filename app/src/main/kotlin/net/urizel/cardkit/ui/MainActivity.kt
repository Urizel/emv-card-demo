package net.urizel.cardkit.ui

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.urizel.cardkit.protocol.CardAdapterImpl
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val cardAdapter = CardAdapterImpl()
    private val viewModel by viewModels<MainViewModel> {
        MainViewModel.factory(cardAdapter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainContent(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("Intent received $intent")
        if (intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            val tagFromIntent: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            Timber.d("Tag $tagFromIntent")

            if (tagFromIntent != null) {
                processTag(tagFromIntent)
            }
        }
    }

    private fun processTag(tag: Tag) {
        val techs = tag.techList
        Timber.d("Tech list: ${techs.joinToString()}")
        val supported = IsoDep::class.qualifiedName
        if (techs.contains(supported)) {
            val isoDep = IsoDep.get(tag)
            val handler = CoroutineExceptionHandler { _, exception ->
                Timber.e(exception)
            }
            lifecycleScope.launch(Dispatchers.IO + handler) {
                cardAdapter.process(isoDep)
            }
        } else {
            Timber.d("Techs don't contain $supported")
        }
    }
}
