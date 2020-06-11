package net.folivo.matrix.bridge.sms.event

import net.folivo.matrix.appservice.api.event.MatrixAppserviceEventService.EventProcessingState
import net.folivo.matrix.bot.appservice.DefaultMatrixAppserviceEventService
import net.folivo.matrix.bot.handler.MatrixEventHandler
import org.neo4j.springframework.data.repository.config.ReactiveNeo4jRepositoryConfigurationExtension
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class SmsMatrixAppserviceEventService(
        private val eventTransactionRepository: EventTransactionRepository,
        eventHandler: List<MatrixEventHandler>
) : DefaultMatrixAppserviceEventService(eventHandler) {

    override fun eventProcessingState(
            tnxId: String,
            eventIdOrType: String
    ): Mono<EventProcessingState> {
        return eventTransactionRepository.findByTnxIdAndEventIdElseType(tnxId, eventIdOrType)
                .map { EventProcessingState.PROCESSED }
                .switchIfEmpty(Mono.just(EventProcessingState.NOT_PROCESSED))
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    override fun saveEventProcessed(tnxId: String, eventIdOrType: String): Mono<Void> {
        return eventTransactionRepository.save(EventTransaction(tnxId, eventIdOrType))
                .then()
    }

}