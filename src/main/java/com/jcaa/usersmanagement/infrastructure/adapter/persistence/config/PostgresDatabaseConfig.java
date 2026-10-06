package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

public record PostgresDatabaseConfig(
    String host,
    int port,
    String databaseName,
    String username,
    String password,
    String sslMode) {
  private static final String URL_TEMPLATE =
      "jdbc:postgresql://%s:%d/%s?sslmode=%s";

  public String buildJdbcUrl() {
    return String.format(URL_TEMPLATE, host, port, databaseName, sslMode);
  }
}