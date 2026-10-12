package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.CreateUserUseCase;
import com.jcaa.usersmanagement.application.port.out.CreateAccountPort;
import com.jcaa.usersmanagement.application.port.out.GetUserByEmailPort;
import com.jcaa.usersmanagement.application.port.out.PublishDomainEventPort;
import com.jcaa.usersmanagement.application.port.out.SaveUserPort;
import com.jcaa.usersmanagement.application.port.out.UpdateUserPort;
import com.jcaa.usersmanagement.application.service.dto.command.CreateUserCommand;
import com.jcaa.usersmanagement.application.service.mapper.UserApplicationMapper;
import com.jcaa.usersmanagement.domain.event.UserCreatedDomainEvent;
import com.jcaa.usersmanagement.domain.exception.AccountCreationFailedException;
import com.jcaa.usersmanagement.domain.exception.UserAlreadyExistsException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateUserService implements CreateUserUseCase {

  private final SaveUserPort saveUserPort;
  private final GetUserByEmailPort getUserByEmailPort;
  private final CreateAccountPort createAccountPort;
  private final UpdateUserPort updateUserPort;
  private final PublishDomainEventPort publishDomainEventPort;
  private final EmailNotificationService emailNotificationService;
  private final Validator validator;

  @Override
  public UserModel execute(final CreateUserCommand command) {
    validateCommand(command);

    final UserEmail email = new UserEmail(command.email());
    ensureEmailIsNotTaken(email);

    final UserModel userToSave = UserApplicationMapper.fromCreateCommandToModel(command);
    final UserModel savedUser = saveUserPort.save(userToSave);

    final UserModel resultUser = activateIfAccountWasCreated(savedUser);

    publishUserCreated(resultUser);
    emailNotificationService.notifyUserCreated(resultUser, command.password());

    return resultUser;
  }

  /** PENDING -> ACTIVE solo si cuentas confirma la cuenta; si no, el usuario queda PENDING. */
  private UserModel activateIfAccountWasCreated(final UserModel savedUser) {
    try {
      createAccountPort.createFor(savedUser);
      return updateUserPort.update(savedUser.activate());
    } catch (final AccountCreationFailedException exception) {
      log.warn(
          "[CreateUserService] usuario {} queda PENDING. Causa: {}",
          savedUser.getId().value(),
          exception.getMessage());
      return savedUser;
    }
  }

  /** Un fallo al publicar el evento no debe tumbar la creación del usuario. */
  private void publishUserCreated(final UserModel user) {
    try {
      publishDomainEventPort.publish(new UserCreatedDomainEvent(user));
    } catch (final RuntimeException exception) {
      log.warn(
          "[CreateUserService] no se pudo publicar user.created de {}: {}",
          user.getId().value(),
          exception.getMessage());
    }
  }

  private void validateCommand(final CreateUserCommand command) {
    final Set<ConstraintViolation<CreateUserCommand>> violations = validator.validate(command);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private void ensureEmailIsNotTaken(final UserEmail email) {
    getUserByEmailPort
        .getByEmail(email)
        .ifPresent(
            ignored -> {
              throw UserAlreadyExistsException.becauseEmailAlreadyExists(email.value());
            });
  }
}