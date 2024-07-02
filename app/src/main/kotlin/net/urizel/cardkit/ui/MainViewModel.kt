package net.urizel.cardkit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.urizel.cardkit.protocol.CardAdapter
import net.urizel.cardkit.protocol.ClearEvent
import net.urizel.cardkit.protocol.Event
import timber.log.Timber

class MainViewModel(
    cardAdapter: CardAdapter
): ViewModel() {
    private val _stateFlow = MutableStateFlow(MainActivityState(arrayListOf()))
    val stateFlow: StateFlow<MainActivityState> = _stateFlow

    init {
        cardAdapter.events
            .onEach { event ->
                Timber.i("Event: $event")
                val state = _stateFlow.value
                val newState = if (event is ClearEvent) {
                    state.copy(
                        events = arrayListOf()
                    )
                } else {
                    state.copy(
                        events = state.events + event
                    )
                }
                _stateFlow.tryEmit(newState)
            }
            .launchIn(viewModelScope)
    }

    companion object {
        fun factory(cardAdapter: CardAdapter) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass == MainViewModel::class.java) {
                    return MainViewModel(cardAdapter) as T
                } else {
                    throw IllegalArgumentException("Unsupported class $modelClass")
                }
            }
        }
    }
}

data class MainActivityState(
    val events: List<Event>
)
