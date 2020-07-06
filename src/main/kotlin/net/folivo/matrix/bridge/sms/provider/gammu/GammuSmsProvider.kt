package net.folivo.matrix.bridge.sms.provider.gammu

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirst
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
import reactor.core.publisher.Flux
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    init {
        LOG.info("Using Gammu as SmsProvider.")
    }

    @EventListener(ContextRefreshedEvent::class)
    suspend fun startNewMessageLookupLoop() {
        GlobalScope.launch {
            while (true) {
                try {
                    Path.of(properties.inboxPath).toFile().walkTopDown().asFlow()
                            .collect { file ->
                                val sender = file.name.substringBeforeLast('_').substringAfterLast('_')
                                val message = Flux.fromStream(Files.lines(file.toPath(), UTF_8))
                                        .skipUntil { it.startsWith("[SMSBackup000]") }
                                        .filter { it.startsWith("; ") }
                                        .map { it.removePrefix("; ") }
                                        .collectList()
                                        .map { it.joinToString(separator = "") }
                                        .doOnSuccess {
                                            Files.move(
                                                    file.toPath(),
                                                    Path.of(properties.inboxProcessedPath, file.name),
                                                    StandardCopyOption.REPLACE_EXISTING
                                            )
                                        }.awaitFirst()
                                receiveSms(sender, message)
                            }
                    LOG.debug("read inbox")
                } catch (error: Throwable) {
                    LOG.error("something unexpected happened while scanning directories for new sms", error)
                }
                delay(10000)
            }
        }
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

    suspend fun receiveSms(
            sender: String,
            body: String
    ) {// FIXME use telephone number utility, because it seems to be possible that sender does not match international regex
        receiveSmsService.receiveSms(body = body, sender = sender)
                ?.also { sendSms(receiver = sender, body = it) }

    }
}