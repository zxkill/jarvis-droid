package org.zxkill.nori.screenshot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.zxkill.nori.di.WakeDeviceWrapper
import org.zxkill.nori.io.wake.WakeState

class FakeWakeDeviceWrapper : WakeDeviceWrapper {
    override val state: StateFlow<WakeState?> = MutableStateFlow(null)
    override val isHeyNori: StateFlow<Boolean> = MutableStateFlow(true)

    override fun download() {}
    override fun processFrame(audio16bitPcm: ShortArray): Boolean = false
    override fun frameSize(): Int = 1312
    override fun reinitializeToReleaseResources() {}
}
