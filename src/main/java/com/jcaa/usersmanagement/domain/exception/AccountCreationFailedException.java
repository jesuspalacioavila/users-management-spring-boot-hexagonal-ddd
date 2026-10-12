package com.jcaa.usersmanagement.domain.exception;

public final class AccountCreationFailedException extends DomainException {

  private static final String MESSAGE_FAILED = "Could not create the account for user '%s'.";
  private static final String MESSAGE_CIRCUIT_OPEN =
      "Accounts service unavailable (circuit open). Account not created for user '%s'.";

  private AccountCreationFailedException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static AccountCreationFailedException becauseServiceFailed(
      final String userId, final Throwable cause) {
    return new AccountCreationFailedException(String.format(MESSAGE_FAILED, userId), cause);
  }

  public static AccountCreationFailedException becauseCircuitIsOpen(final String userId) {
    return new AccountCreationFailedException(String.format(MESSAGE_CIRCUIT_OPEN, userId), null);
  }
}