package net.folivo.matrix.bridge.sms.provider.gammu

import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8


// TODO Tests!
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "matrix.bridge.sms.provider.gammu", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(GammuSmsProviderProperties::class)
class GammuSmsProvider(
        private val properties: GammuSmsProviderProperties,
        private val receiveSmsService: ReceiveSmsService
) : SmsProvider {

    private val logger = LoggerFactory.getLogger(GammuSmsProvider::class.java)

    init {
        logger.info("Using Gammu as SmsProvider.")
    }

    private var disposable: Disposable? = null

    @EventListener(ContextRefreshedEvent::class)
    fun startNewMessageLookupLoop() {
        disposable = Mono.just(true) // TODO is there a less hacky way? Without that line, repeat does not call getBatchToken
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
                            .map { Pair(sender, it.joinToString()) }
                            .doOnSuccess {
                                Files.move(file.toPath(), Path.of(properties.inboxProcessedPath, file.name))
                            }
                }.flatMap { message ->
                    receiveSms(message.first, message.second)
                }
                .doOnComplete { logger.debug("read inbox") }
                .doOnError { logger.error("something happened while scanning directories for new sms", it) }
                .delaySubscription(Duration.ofSeconds(10))
                .repeat()
                .retry()
                .subscribe()
    }

    override fun sendSms(receiver: String, body: String): Mono<Void> {
        return Mono.create {
            try {
                var exitCode: Int
                val output = ProcessBuilder(
                        listOf(
                                "gammu-smsd-inject",
                                "TEXT",
                                receiver,
                                "-len",
                                body.length.toString(),
                                "-unicode",
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
                if (exitCode != 0)
                    it.error(
                            MatrixServerException(
                                    INTERNAL_SERVER_ERROR,
                                    ErrorResponse(
                                            "NET.FOLIVO_INTERNAL_SERVER_ERROR",
                                            "exitCode was $exitCode due to $output"
                                    )
                            )
                    )
                else {
                    logger.debug(output)
                    it.success()
                }
            } catch (e: Exception) {
                logger.error("some unhandled exception occurred during running send sms shell command", e)
                it.error(
                        MatrixServerException(
                                INTERNAL_SERVER_ERROR,
                                ErrorResponse("M.FOLIVO_UNKNOWN", e.message)
                        )
                )
            }
        }
    }

    fun receiveSms(sender: String, body: String): Mono<Void> {
        return receiveSmsService.receiveSms(body = body, sender = sender)
                .flatMap { sendSms(receiver = sender, body = it) }
    }
}