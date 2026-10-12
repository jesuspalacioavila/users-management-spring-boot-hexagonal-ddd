package com.jcaa.usersmanagement.infrastructure.adapter.events;

import java.util.Map;

public record NotificationEventMessage(
    String eventId, String type, String occurredAt, Recipient recipient, Map<String, String> data) {

  public record Recipient(String email, String name) {}
}