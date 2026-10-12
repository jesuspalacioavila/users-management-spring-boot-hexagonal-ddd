package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.domain.event.DomainEvent;

public interface PublishDomainEventPort {
  void publish(DomainEvent event);
}