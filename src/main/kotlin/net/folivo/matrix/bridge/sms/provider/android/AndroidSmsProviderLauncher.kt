package net.folivo.matrix.bridge.sms.provider.android

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

class AndroidSmsProviderLauncher(private val androidSmsProvider: AndroidSmsProvider) {

    @EventListener(ApplicationReadyEvent::class)
    fun startLoop() {
        GlobalScope.launch {
            androidSmsProvider.startNewMessageLookupLoop()
        }
    }
}