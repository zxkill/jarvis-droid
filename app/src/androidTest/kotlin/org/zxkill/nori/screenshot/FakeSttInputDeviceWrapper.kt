package org.zxkill.nori.screenshot

import kotlinx.coroutines.flow.MutableStateFlow
import org.zxkill.nori.di.SttInputDeviceWrapper
import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.io.input.SttState

class FakeSttInputDeviceWrapper : SttInputDeviceWrapper {
    override val uiState: MutableStateFlow<SttState> = MutableStateFlow(SttState.NotInitialized)

    override fun tryLoad(
        thenStartListeningEventListener: ((InputEvent) -> Unit)?,
        playSound: Boolean,
    ): Boolean {
        return true
    }

    override fun stopListening() {
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
    }

    override fun reinitializeToReleaseResources() {
    }
}
