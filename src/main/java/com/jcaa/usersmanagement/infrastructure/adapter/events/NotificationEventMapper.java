package com.jcaa.usersmanagement.infrastructure.adapter.events;

import com.jcaa.usersmanagement.domain.event.DomainEvent;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class NotificationEventMapper {

  private static final Map<String, String> TYPES =
      Map.of("user.created", "USER_CREATED", "user.updated", "USER_UPDATED");

  private NotificationEventMapper() {}

  /** Devuelve vacío si el evento no se notifica por correo (por ejemplo user.deleted). */
  static Optional<NotificationEventMessage> from(final DomainEvent event) {
    final String type = TYPES.get(event.getEventName());
    if (type == null) {
      return Optional.empty();
    }
    final Map<String, String> payload = event.payload();
    return Optional.of(
        new NotificationEventMessage(
            UUID.randomUUID().toString(),
            type,
            event.getOccurredOn().atZone(ZoneId.systemDefault()).toInstant().toString(),
            new NotificationEventMessage.Recipient(payload.get("email"), payload.get("name")),
            Map.of(
                "userId", payload.get("id"),
                "role", payload.get("role"),
                "status", payload.get("status"))));
  }
}