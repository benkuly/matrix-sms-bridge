package net.folivo.matrix.bridge.sms.event

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.event.MatrixAppserviceEventService.EventProcessingState.NOT_PROCESSED
import net.folivo.matrix.appservice.api.event.MatrixAppserviceEventService.EventProcessingState.PROCESSED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class SmsMatrixAppserviceEventServiceTest {

    @MockK
    lateinit var eventTransactionRepositoryMock: EventTransactionRepository

    @Test
    fun `eventProcessingState should be PROCESSED when already in database`() {
        val cut = SmsMatrixAppserviceEventService(eventTransactionRepositoryMock, emptyList())

        every { eventTransactionRepositoryMock.findByTnxIdAndEventIdElseType("someTnxId", "someEventIdOrType") }
                .returns(Mono.just(EventTransaction("someTnxId", "someEventIdOrType")))

        val result = runBlocking {
            cut.eventProcessingState("someTnxId", "someEventIdOrType")
        }
        assertThat(result).isEqualTo(PROCESSED)
    }

    @Test
    fun `eventProcessingState should be NOT_PROCESSED when not in database`() {
        val cut = SmsMatrixAppserviceEventService(eventTransactionRepositoryMock, emptyList())

        every { eventTransactionRepositoryMock.findByTnxIdAndEventIdElseType("someTnxId", "someEventIdOrType") }
                .returns(Mono.empty())

        val result = runBlocking {
            cut.eventProcessingState("someTnxId", "someEventIdOrType")
        }
        assertThat(result).isEqualTo(NOT_PROCESSED)
    }

    @Test
    fun `should save processed event in database`() {
        val cut = SmsMatrixAppserviceEventService(eventTransactionRepositoryMock, emptyList())

        every { eventTransactionRepositoryMock.save<EventTransaction>(any()) }
                .returns(Mono.just(EventTransaction("someTnxId", "someEventIdOrType")))

        runBlocking { cut.saveEventProcessed("someTnxId", "someEventIdOrType") }

        verify { eventTransactionRepositoryMock.save(EventTransaction("someTnxId", "someEventIdOrType")) }
    }


}