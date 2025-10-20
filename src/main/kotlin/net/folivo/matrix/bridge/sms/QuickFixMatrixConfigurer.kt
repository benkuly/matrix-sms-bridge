package net.folivo.matrix.bridge.sms

import com.fasterxml.jackson.annotation.JsonProperty
import net.folivo.matrix.core.annotation.MatrixEvent
import net.folivo.matrix.core.config.MatrixConfiguration
import net.folivo.matrix.core.config.MatrixConfigurer
import net.folivo.matrix.core.model.MatrixId.*
import net.folivo.matrix.core.model.events.StandardStateEvent
import net.folivo.matrix.core.model.events.StateEventContent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QuickFixConfiguration {
    @Bean
    fun matrixConfiguration(configurer: List<MatrixConfigurer>): MatrixConfiguration {
        val config = MatrixConfiguration()
        configurer.forEach {
            it.configure(config)
        }
        QuickFixMatrixConfigurer().configure(config)
        return config
    }
}

class QuickFixMatrixConfigurer : MatrixConfigurer {
    override fun configure(config: MatrixConfiguration) {
        config.configure {
            registerMatrixEvents(
                QuickFixCreateEvent::class.java,
            )
        }
    }
}

@MatrixEvent("m.room.create")
class QuickFixCreateEvent : StandardStateEvent<QuickFixCreateEvent.CreateEventContent> {

    constructor(
        content: CreateEventContent,
        id: EventId,
        sender: UserId,
        originTimestamp: Long,
        roomId: RoomId? = null,
        unsigned: UnsignedData,
        previousContent: CreateEventContent? = null
    ) : super(
        type = "m.room.create",
        content = content,
        id = id,
        sender = sender,
        originTimestamp = originTimestamp,
        roomId = roomId,
        unsigned = unsigned,
        stateKey = "",
        previousContent = previousContent
    )

    data class CreateEventContent(
        @JsonProperty("creator")
        val creator: UserId? = null,
        @JsonProperty("m.federate")
        val federate: Boolean = true,
        @JsonProperty("room_version")
        val roomVersion: String = "1",
        @JsonProperty("predecessor")
        val predecessor: PreviousRoom? = null
    ) : StateEventContent {
        data class PreviousRoom(
            @JsonProperty("room_id")
            val roomId: RoomId,
            @JsonProperty("event_id")
            val eventId: EventId
        )
    }
}