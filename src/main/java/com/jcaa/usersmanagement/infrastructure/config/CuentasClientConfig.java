package com.jcaa.usersmanagement.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class CuentasClientConfig {

  private static final String NAME = "cuentas";

  @Bean
  public RestClient cuentasRestClient(
      @Value("${cuentas.base-url}") final String baseUrl,
      @Value("${cuentas.connect-timeout-ms}") final int connectTimeoutMs,
      @Value("${cuentas.read-timeout-ms}") final int readTimeoutMs) {
    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMs);
    factory.setReadTimeout(readTimeoutMs);
    log.info("[CuentasClientConfig] cliente REST hacia cuentas: {}", baseUrl);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Bean
  public CircuitBreaker cuentasCircuitBreaker() {
    final CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordException(CuentasClientConfig::isTransientFailure)
            .build();
    final CircuitBreaker circuitBreaker = CircuitBreaker.of(NAME, config);
    circuitBreaker
        .getEventPublisher()
        .onStateTransition(
            event -> log.warn("[CuentasCircuitBreaker] cambio de estado: {}", event.getStateTransition()));
    return circuitBreaker;
  }

  @Bean
  public Retry cuentasRetry(
      @Value("${cuentas.retry.max-attempts}") final int maxAttempts,
      @Value("${cuentas.retry.initial-interval-ms}") final long initialIntervalMs) {
    final RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(initialIntervalMs, 2.0))
            .retryOnException(CuentasClientConfig::isTransientFailure)
            .build();
    final Retry retry = Retry.of(NAME, config);
    retry
        .getEventPublisher()
        .onRetry(
            event ->
                log.warn(
                    "[CuentasRetry] intento {} de {} fallido. Causa: {}",
                    event.getNumberOfRetryAttempts(),
                    maxAttempts,
                    String.valueOf(event.getLastThrowable())));
    return retry;
  }

  /** Solo se reintenta (y cuenta para abrir el circuito) si cuentas está caído o falla (5xx). */
  private static boolean isTransientFailure(final Throwable throwable) {
    return throwable instanceof ResourceAccessException
        || throwable instanceof HttpServerErrorException;
  }
}