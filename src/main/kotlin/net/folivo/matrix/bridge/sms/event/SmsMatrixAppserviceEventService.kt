package net.folivo.matrix.bridge.sms.event

import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.appservice.api.event.MatrixAppserviceEventService.EventProcessingState
import net.folivo.matrix.bot.appservice.DefaultMatrixAppserviceEventService
import net.folivo.matrix.bot.handler.MatrixEventHandler
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceEventService(
        private val eventTransactionRepository: EventTransactionRepository,
        eventHandler: List<MatrixEventHandler>
) : DefaultMatrixAppserviceEventService(eventHandler) {

    override suspend fun eventProcessingState(
            tnxId: String,
            eventIdOrType: String
    ): EventProcessingState {
        return if (eventTransactionRepository.findByTnxIdAndEventIdElseType(tnxId, eventIdOrType)
                        .awaitFirstOrNull() != null)
            EventProcessingState.PROCESSED
        else
            EventProcessingState.NOT_PROCESSED
    }

    override suspend fun saveEventProcessed(tnxId: String, eventIdOrType: String) {
        eventTransactionRepository.save(EventTransaction(tnxId, eventIdOrType)).awaitFirstOrNull()
    }

}