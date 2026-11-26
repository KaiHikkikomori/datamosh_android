package ru.datamosh.radio

import kotlinx.coroutines.flow.MutableStateFlow

/** Whether the radio is (supposed to be) playing, shared between the service and the home-screen widget. */
object RadioPlayback {
    val playing = MutableStateFlow(false)
}
