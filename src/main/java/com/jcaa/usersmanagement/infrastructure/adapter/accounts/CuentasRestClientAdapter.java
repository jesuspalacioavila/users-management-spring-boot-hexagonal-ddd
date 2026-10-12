package com.jcaa.usersmanagement.infrastructure.adapter.accounts;

import com.jcaa.usersmanagement.application.port.out.CreateAccountPort;
import com.jcaa.usersmanagement.domain.exception.AccountCreationFailedException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CuentasRestClientAdapter implements CreateAccountPort {

  private static final String ACCOUNTS_URI = "/api/accounts";

  private final RestClient cuentasRestClient;
  private final CircuitBreaker cuentasCircuitBreaker;
  private final Retry cuentasRetry;

  @Override
  public void createFor(final UserModel user) {
    final String userId = user.getId().value();
    final Map<String, String> body =
        Map.of(
            "userId", userId,
            "name", user.getName().value(),
            "email", user.getEmail().value());

    final Supplier<Void> call = () -> postAccount(userId, body);
    final Supplier<Void> withCircuitBreaker =
        CircuitBreaker.decorateSupplier(cuentasCircuitBreaker, call);
    final Supplier<Void> withRetry = Retry.decorateSupplier(cuentasRetry, withCircuitBreaker);

    try {
      withRetry.get();
    } catch (final CallNotPermittedException exception) {
      throw AccountCreationFailedException.becauseCircuitIsOpen(userId);
    } catch (final RestClientException exception) {
      throw AccountCreationFailedException.becauseServiceFailed(userId, exception);
    }
  }

  private Void postAccount(final String userId, final Map<String, String> body) {
    try {
      cuentasRestClient
          .post()
          .uri(ACCOUNTS_URI)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
      log.info("[CuentasRestClientAdapter] cuenta creada para userId={}", userId);
    } catch (final HttpClientErrorException.Conflict conflict) {
      // 409: el usuario ya tiene cuenta -> la operación es idempotente, se toma como éxito
      log.info("[CuentasRestClientAdapter] userId={} ya tenía cuenta (409), se continúa", userId);
    }
    return null;
  }
}