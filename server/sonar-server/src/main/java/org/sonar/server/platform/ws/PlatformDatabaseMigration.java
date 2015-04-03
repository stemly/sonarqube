package org.sonar.server.platform.ws;

import org.sonar.server.platform.ruby.RubyBridge;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class PlatformDatabaseMigration implements DatabaseMigration {

  private final RubyBridge rubyBridge;
  /**
   * This lock implements thread safety from concurrent calls of method {@link #startIt()}
   */
  private final ReentrantLock lock = new ReentrantLock();
  /**
   * ExecutorService implements threads management.
   */
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  /**
   * This property acts as a semaphore to make sure at most one db migration task is created at a time.
   * <p>
   * It is set to {@code true} by the first thread to execute the {@link #startIt()} method and set to {@code false}
   * by the thread executing the db migration.
   * </p>
   */
  private AtomicBoolean running = new AtomicBoolean(false);
  private Status status = Status.NONE;
  @Nullable
  private Date startDate;
  @Nullable
  private Throwable failureError;

  public PlatformDatabaseMigration(RubyBridge rubyBridge) {
    this.rubyBridge = rubyBridge;
  }

  /**
   * Shuts down the executor service. If a db migration is running, the termination will take at most 10 seconds.
   * <p>Method called by the pico container when platform is shutting down.</p>
   */
  public void stop() {
    executorService.shutdown(); // Disable new tasks from being submitted
    try {
      // Wait a while for existing tasks to terminate
      if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
        executorService.shutdownNow(); // Cancel currently executing tasks
        // Wait a while for tasks to respond to being cancelled
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
          System.err.println("DBMigration pool did not terminate");
        }
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      executorService.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void startIt() {
    if (lock.isLocked() || this.running.get() /* fail-fast if db migration is running */) {
      return;
    }

    lock.lock();
    try {
      startAsynchronousDBMigration();
    } finally {
      lock.unlock();
    }
  }

  /**
   * This method is not thread safe and must be external protected from concurrent executions.
   */
  private void startAsynchronousDBMigration() {
    if (this.running.get()) {
      return;
    }

    running.getAndSet(true);
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        status = Status.RUNNING;
        startDate = new Date();
        try {
          rubyBridge.databaseMigration().trigger();
        } catch (Throwable t) {
          status = Status.SUCCEEDED;
          failureError = t;
        } finally {
          running.getAndSet(false);
        }
      }
    });
  }

  @Override
  @Nullable
  public Date startedAt() {
    return this.startDate;
  }

  @Override
  public Status status() {
    return this.status;
  }

  @Nullable
  @Override
  public Throwable failureError() {
    return this.failureError;
  }
}
