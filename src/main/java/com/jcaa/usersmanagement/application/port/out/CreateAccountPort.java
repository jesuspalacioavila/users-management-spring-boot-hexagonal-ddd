package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.domain.valueobject.UserId;

public interface CreateAccountPort {
  void createFor(UserId userId);
}