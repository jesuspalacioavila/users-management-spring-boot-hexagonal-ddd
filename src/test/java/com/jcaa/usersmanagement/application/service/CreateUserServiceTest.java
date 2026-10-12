package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jcaa.usersmanagement.application.port.out.CreateAccountPort;
import com.jcaa.usersmanagement.application.port.out.GetUserByEmailPort;
import com.jcaa.usersmanagement.application.port.out.PublishDomainEventPort;
import com.jcaa.usersmanagement.application.port.out.SaveUserPort;
import com.jcaa.usersmanagement.application.port.out.UpdateUserPort;
import com.jcaa.usersmanagement.application.service.dto.command.CreateUserCommand;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.event.UserCreatedDomainEvent;
import com.jcaa.usersmanagement.domain.exception.AccountCreationFailedException;
import com.jcaa.usersmanagement.domain.exception.UserAlreadyExistsException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("CreateUserService")
@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

  @Mock private SaveUserPort saveUserPort;
  @Mock private GetUserByEmailPort getUserByEmailPort;
  @Mock private CreateAccountPort createAccountPort;
  @Mock private UpdateUserPort updateUserPort;
  @Mock private PublishDomainEventPort publishDomainEventPort;
  @Mock private EmailNotificationService emailNotificationService;

  private CreateUserService service;

  @BeforeEach
  void setUp() {
    try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
      service =
          new CreateUserService(
              saveUserPort,
              getUserByEmailPort,
              createAccountPort,
              updateUserPort,
              publishDomainEventPort,
              emailNotificationService,
              validatorFactory.getValidator());
    }
  }

  // ── flujo feliz

  @Test
  @DisplayName("execute() guarda, crea la cuenta, activa, publica el evento y notifica")
  void shouldSaveCreateAccountActivatePublishAndNotifyWhenEmailIsNew() {
    // Arrange
    final CreateUserCommand command =
        new CreateUserCommand("u-01", "John Arrieta", "john@example.com", "Pass1234", "ADMIN");

    final UserModel savedUser =
        new UserModel(
            new UserId("u-01"),
            new UserName("John Arrieta"),
            new UserEmail("john@example.com"),
            UserPassword.fromPlainText("Pass1234"),
            UserRole.ADMIN,
            UserStatus.PENDING);
    final UserModel activatedUser = savedUser.activate();

    when(getUserByEmailPort.getByEmail(any())).thenReturn(Optional.empty());
    when(saveUserPort.save(any())).thenReturn(savedUser);
    when(updateUserPort.update(any())).thenReturn(activatedUser);

    // Act
    final UserModel result = service.execute(command);

    // Assert
    assertAll(
        "flujo feliz de CreateUserService",
        () -> assertNotNull(result, "resultado no debe ser null"),
        () -> assertEquals("u-01", result.getId().value(), "id del usuario guardado"),
        () -> assertEquals(UserStatus.ACTIVE, result.getStatus(), "queda ACTIVE"));

    verify(saveUserPort).save(any(UserModel.class));
    verify(createAccountPort).createFor(savedUser);
    verify(updateUserPort).update(activatedUser);
    verify(publishDomainEventPort).publish(any(UserCreatedDomainEvent.class));
    verify(emailNotificationService).notifyUserCreated(activatedUser, "Pass1234");
  }

  // ── cuentas no disponible

  @Test
  @DisplayName("execute() deja al usuario PENDING cuando no se puede crear la cuenta")
  void shouldKeepUserPendingWhenAccountCreationFails() {
    // Arrange
    final CreateUserCommand command =
        new CreateUserCommand("u-03", "Ana Perez", "ana@example.com", "Pass9999", "MEMBER");

    final UserModel savedUser =
        new UserModel(
            new UserId("u-03"),
            new UserName("Ana Perez"),
            new UserEmail("ana@example.com"),
            UserPassword.fromPlainText("Pass9999"),
            UserRole.MEMBER,
            UserStatus.PENDING);

    when(getUserByEmailPort.getByEmail(any())).thenReturn(Optional.empty());
    when(saveUserPort.save(any())).thenReturn(savedUser);
    doThrow(AccountCreationFailedException.becauseCircuitIsOpen("u-03"))
        .when(createAccountPort)
        .createFor(any());

    // Act
    final UserModel result = service.execute(command);

    // Assert
    assertEquals(UserStatus.PENDING, result.getStatus(), "sigue PENDING");
    verify(updateUserPort, never()).update(any());
    verify(publishDomainEventPort).publish(any(UserCreatedDomainEvent.class));
    verify(emailNotificationService).notifyUserCreated(savedUser, "Pass9999");
  }

  // ── el evento falla pero el usuario se crea igual

  @Test
  @DisplayName("execute() no falla cuando la publicación del evento lanza una excepción")
  void shouldNotFailWhenEventPublishingThrows() {
    // Arrange
    final CreateUserCommand command =
        new CreateUserCommand("u-04", "Luis Gomez", "luis@example.com", "Pass7777", "MEMBER");

    final UserModel savedUser =
        new UserModel(
            new UserId("u-04"),
            new UserName("Luis Gomez"),
            new UserEmail("luis@example.com"),
            UserPassword.fromPlainText("Pass7777"),
            UserRole.MEMBER,
            UserStatus.PENDING);
    final UserModel activatedUser = savedUser.activate();

    when(getUserByEmailPort.getByEmail(any())).thenReturn(Optional.empty());
    when(saveUserPort.save(any())).thenReturn(savedUser);
    when(updateUserPort.update(any())).thenReturn(activatedUser);
    doThrow(new IllegalStateException("kafka caído"))
        .when(publishDomainEventPort)
        .publish(any());

    // Act
    final UserModel result = service.execute(command);

    // Assert
    assertEquals(UserStatus.ACTIVE, result.getStatus());
    verify(emailNotificationService).notifyUserCreated(activatedUser, "Pass7777");
  }

  // ── email duplicado

  @Test
  @DisplayName("execute() lanza UserAlreadyExistsException cuando el email ya está registrado")
  void shouldThrowWhenEmailAlreadyExists() {
    // Arrange
    final CreateUserCommand command =
        new CreateUserCommand("u-02", "Jane Doe", "jane@example.com", "Pass5678", "MEMBER");

    final UserModel existing =
        new UserModel(
            new UserId("u-99"),
            new UserName("Jane Doe"),
            new UserEmail("jane@example.com"),
            UserPassword.fromPlainText("OtraPass1"),
            UserRole.MEMBER,
            UserStatus.ACTIVE);

    when(getUserByEmailPort.getByEmail(any())).thenReturn(Optional.of(existing));

    // Act & Assert
    assertThrows(UserAlreadyExistsException.class, () -> service.execute(command));
    verify(saveUserPort, never()).save(any());
    verify(createAccountPort, never()).createFor(any());
    verify(publishDomainEventPort, never()).publish(any());
    verify(emailNotificationService, never()).notifyUserCreated(any(), any());
  }

  // ── validación del command

  @Test
  @DisplayName(
      "execute() lanza ConstraintViolationException cuando el command tiene campos inválidos")
  void shouldThrowWhenCommandIsInvalid() {
    // Arrange — id en blanco y email inválido
    final CreateUserCommand command =
        new CreateUserCommand("", "Jo", "not-an-email", "short", "ADMIN");

    // Act & Assert
    assertThrows(ConstraintViolationException.class, () -> service.execute(command));
    verifyNoInteractions(
        saveUserPort,
        getUserByEmailPort,
        createAccountPort,
        updateUserPort,
        publishDomainEventPort,
        emailNotificationService);
  }
}