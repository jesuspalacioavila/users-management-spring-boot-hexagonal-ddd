package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.UpdateUserUseCase;
import com.jcaa.usersmanagement.application.port.out.GetUserByEmailPort;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.PublishDomainEventPort;
import com.jcaa.usersmanagement.application.port.out.UpdateUserPort;
import com.jcaa.usersmanagement.application.service.dto.command.UpdateUserCommand;
import com.jcaa.usersmanagement.application.service.mapper.UserApplicationMapper;
import com.jcaa.usersmanagement.domain.event.UserUpdatedDomainEvent;
import com.jcaa.usersmanagement.domain.exception.UserAlreadyExistsException;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
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
public class UpdateUserService implements UpdateUserUseCase {

  private final UpdateUserPort updateUserPort;
  private final GetUserByIdPort getUserByIdPort;
  private final GetUserByEmailPort getUserByEmailPort;
  private final PublishDomainEventPort publishDomainEventPort;
  private final Validator validator;

  @Override
  public UserModel execute(final UpdateUserCommand command) {
    validateCommand(command);

    final UserId userId = new UserId(command.id());
    final UserModel current = findExistingUserOrFail(userId);
    final UserEmail newEmail = new UserEmail(command.email());

    ensureEmailIsNotTakenByAnotherUser(newEmail, userId);

    final UserModel userToUpdate =
        UserApplicationMapper.fromUpdateCommandToModel(command, current.getPassword());
    final UserModel updatedUser = updateUserPort.update(userToUpdate);

    publishUserUpdated(updatedUser);

    return updatedUser;
  }

  /** Un fallo al publicar el evento no debe tumbar la actualización del usuario. */
  private void publishUserUpdated(final UserModel user) {
    try {
      publishDomainEventPort.publish(new UserUpdatedDomainEvent(user));
    } catch (final RuntimeException exception) {
      log.warn(
          "[UpdateUserService] no se pudo publicar user.updated de {}: {}",
          user.getId().value(),
          exception.getMessage());
    }
  }

  private void validateCommand(final UpdateUserCommand command) {
    final Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private UserModel findExistingUserOrFail(final UserId userId) {
    return getUserByIdPort
        .getById(userId)
        .orElseThrow(() -> UserNotFoundException.becauseIdWasNotFound(userId.value()));
  }

  private void ensureEmailIsNotTakenByAnotherUser(final UserEmail newEmail, final UserId ownerId) {
    getUserByEmailPort
        .getByEmail(newEmail)
        .ifPresent(
            found -> {
              if (!found.getId().equals(ownerId)) {
                throw UserAlreadyExistsException.becauseEmailAlreadyExists(newEmail.value());
              }
            });
  }
}