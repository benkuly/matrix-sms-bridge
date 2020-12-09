package net.folivo.matrix.bridge.sms.provider.gammu

import kotlinx.coroutines.reactor.mono
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8


@Profile("!initialsync")
@Component
@ConditionalOnProperty(prefix = "matrix.bridge.sms.provider.gammu", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(GammuSmsProviderProperties::class)
class GammuSmsProvider(
    private val properties: GammuSmsProviderProperties,
    private val receiveSmsService: ReceiveSmsService,
    private val phoneNumberService: PhoneNumberService
) : SmsProvider {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    init {
        LOG.info("Using Gammu as SmsProvider.")
    }

    private var disposable: Disposable? = null

    @EventListener(ApplicationReadyEvent::class)
    fun startNewMessageLookupLoop() {
        disposable = Mono.just(true) // TODO is there a less hacky way? Without that line, repeat does not work
            .flatMapMany { Flux.fromStream(Files.list(Path.of(properties.inboxPath))) }
            .map { it.toFile() }
            .flatMap { file ->
                val name = file.name
                val sender = name.substringBeforeLast('_').substringAfterLast('_')
                Flux.fromStream(Files.lines(file.toPath(), UTF_8))
                    .skipUntil { it.startsWith("[SMSBackup000]") }
                    .filter { it.startsWith("; ") }
                    .map { it.removePrefix("; ") }
                    .collectList()
                    .map { Pair(sender, it.joinToString(separator = "")) }
                    .doOnSuccess {
                        Files.move(
                            file.toPath(),
                            Path.of(properties.inboxProcessedPath, file.name),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
            }.flatMap { message ->
                receiveSms(message.first, message.second)
            }
            .doOnComplete { LOG.debug("read inbox") }
            .doOnError { LOG.error("something happened while scanning directories for new sms", it) }
            .delaySubscription(Duration.ofSeconds(10))
            .repeat()
            .retry()
            .subscribe()
    }

    override suspend fun sendSms(receiver: String, body: String) {
        var exitCode: Int
        val output = try {
            ProcessBuilder(
                listOf(
                    "gammu-smsd-inject",
                    "TEXT",
                    receiver,
                    "-len",
                    body.length.toString(),
                    "-unicode", // TODO maybe don't sent everything in unicode (it allows more characters per sms)
                    "-text",
                    body
                )
            ).directory(File("."))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start().apply {
                    waitFor(10, TimeUnit.SECONDS)
                    exitCode = exitValue()
                }
                .inputStream.bufferedReader().readText()
        } catch (error: Throwable) {
            LOG.error("some unhandled exception occurred during running send sms shell command", error)
            throw            MatrixServerException(
                INTERNAL_SERVER_ERROR,
                ErrorResponse("NET.FOLIVO_UNKNOWN", error.message)
            )
        }
        if (exitCode != 0) {
            throw MatrixServerException(
                INTERNAL_SERVER_ERROR,
                ErrorResponse(
                    "NET.FOLIVO_INTERNAL_SERVER_ERROR",
                    "exitCode was $exitCode due to $output"
                )
            )
        } else {
            LOG.debug(output)
            return
        }

    }

    fun receiveSms(
        sender: String,
        body: String
    ): Mono<Void> {
        return mono {
            val phoneNumber = phoneNumberService.parseToInternationalNumber(sender)
            receiveSmsService.receiveSms(body = body, sender = phoneNumber)
                ?.also { sendSms(receiver = phoneNumber, body = it) }
        }.then()
    }
}