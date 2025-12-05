package com.logforge.external.config

import io.netty.channel.ChannelOption
import kotlinx.coroutines.sync.Semaphore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

/**
 * 외부 로그 API 호출에 필요한 WebClient 및 동시성 제어 리소스를 설정
 */
@Configuration
class ExternalClientConfig {

    /**
     * 외부 API 호출용 WebClient
     * - Netty 타임아웃을 설정해 비정상 지연을 조기에 차단
     */
    @Bean
    fun externalWebClient(builder: WebClient.Builder, properties: ExternalClientProperties): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeoutMillis)
            .responseTimeout(Duration.ofMillis(properties.responseTimeoutMillis))

        return builder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    /**
     * 외부 API 호출 동시성 제한용 세마포어
     * - 테넌트별 병렬 호출 시 글로벌 상한을 부여해 백엔드 과부하를 방지
     */
    @Bean
    fun externalClientSemaphore(properties: ExternalClientProperties): Semaphore =
        Semaphore(properties.maxConcurrentRequests)
}
