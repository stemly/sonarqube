package org.sonar.server.platform.ws;

import javax.annotation.Nullable;
import java.util.Date;

public interface DatabaseMigration {
  enum Status {
    NONE, RUNNING, FAILED, SUCCEEDED
  }

  /**
   * Starts the migration status and returns immediately.
   * <p>
   * Migration can not be started twice but calling this method wont raise an error.
   * On the other hand, calling this method when no migration is needed will start the process anyway.
   * </p>
   * <p>
   * <strong>Do not rename this method to {@code start} otherwise it will be called by the pico container</strong>
   * </p>
   */
  void startIt();

  /**
   * The time and day the last migration was started.
   * <p>
   * If no migration was ever started, the returned date is {@code null}. This value is reset when {@link #startIt()} is
   * called.
   * </p>
   *
   * @return a {@link Date} or {@code null}
   */
  @Nullable
  Date startedAt();

  /**
   * Current status of the migration.
   *
   * @return a {@link Status}
   */
  Status status();

  /**
   * The error of the last migration if it failed, otherwise {@code null}.
   * <p>
   * This value is reset when {@link #startIt()} is called.
   * </p>
   * @return a {@link Throwable} or {@code null}
   */
  @Nullable
  Throwable failureError();

}
