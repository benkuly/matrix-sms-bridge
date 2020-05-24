package net.folivo.matrix.bridge.sms.event

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.folivo.matrix.appservice.api.event.MatrixAppserviceEventService.EventProcessingState.NOT_PROCESSED
import net.folivo.matrix.appservice.api.event.MatrixAppserviceEventService.EventProcessingState.PROCESSED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsMatrixAppserviceEventServiceTest {

    @MockK
    lateinit var eventTransactionRepositoryMock: EventTransactionRepository

    @Test
    fun `eventProcessingState should be PROCESSED when already in database`() {
        val cut = SmsMatrixAppserviceEventService(eventTransactionRepositoryMock, emptyList())

        every { eventTransactionRepositoryMock.findByTnxIdAndEventIdElseType("someTnxId", "someEventIdOrType") }
                .returns(Mono.just(EventTransaction("someTnxId", "someEventIdOrType")))

        StepVerifier
                .create(cut.eventProcessingState("someTnxId", "someEventIdOrType"))
                .assertNext { assertThat(it).isEqualTo(PROCESSED) }
                .verifyComplete()
    }

    @Test
    fun `eventProcessingState should be NOT_PROCESSED when not in database`() {
        val cut = SmsMatrixAppserviceEventService(eventTransactionRepositoryMock, emptyList())

        every { eventTransactionRepositoryMock.findByTnxIdAndEventIdElseType("someTnxId", "someEventIdOrType") }
                .returns(Mono.empty())

        StepVerifier
                .create(cut.eventProcessingState("someTnxId", "someEventIdOrType"))
                .assertNext { assertThat(it).isEqualTo(NOT_PROCESSED) }
                .verifyComplete()
    }

    @Test
    fun `should save processed event in database`() {
        val cut = SmsMatrixAppserviceEventService(eventTransactionRepositoryMock, emptyList())

        every { eventTransactionRepositoryMock.save<EventTransaction>(any()) }
                .returns(Mono.just(EventTransaction("someTnxId", "someEventIdOrType")))

        StepVerifier
                .create(cut.saveEventProcessed("someTnxId", "someEventIdOrType"))
                .verifyComplete()

        verify { eventTransactionRepositoryMock.save(EventTransaction("someTnxId", "someEventIdOrType")) }
    }


}