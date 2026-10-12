package com.jcaa.usersmanagement.infrastructure.adapter.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.port.out.PublishDomainEventPort;
import com.jcaa.usersmanagement.domain.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publicador simulado: imprime el JSON en el log. Se activa con events.publisher=log. */
@Slf4j
@Component
@ConditionalOnProperty(name = "events.publisher", havingValue = "log", matchIfMissing = true)
public class LogDomainEventPublisher implements PublishDomainEventPort {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void publish(final DomainEvent event) {
    NotificationEventMapper.from(event)
        .ifPresentOrElse(
            message ->
                log.info(
                    "[LogDomainEventPublisher] evento (simulado, sin Kafka): {}", toJson(message)),
            () ->
                log.debug(
                    "[LogDomainEventPublisher] evento {} no genera notificación", event.getEventName()));
  }

  private String toJson(final NotificationEventMessage message) {
    try {
      return objectMapper.writeValueAsString(message);
    } catch (final JsonProcessingException exception) {
      throw new IllegalStateException("No se pudo serializar el evento", exception);
    }
  }
}