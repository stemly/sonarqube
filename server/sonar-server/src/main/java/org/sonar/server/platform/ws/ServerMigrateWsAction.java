/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.platform.ws;

import com.google.common.io.Resources;
import org.sonar.api.platform.ServerUpgradeStatus;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.persistence.Database;

import static org.sonar.server.platform.ws.DatabaseMigration.Status.NONE;
import static org.sonar.server.platform.ws.DatabaseMigration.Status.SUCCEEDED;

public class ServerMigrateWsAction implements ServerWsAction {

  private static final String UNSUPPORTED_DATABASE_MIGRATION_STATUS = "Unsupported DatabaseMigration status";

  private final ServerUpgradeStatus serverUpgradeStatus;
  private final DatabaseMigration databaseMigration;
  private final Database database;

  public ServerMigrateWsAction(ServerUpgradeStatus serverUpgradeStatus,
                               Database database,
                               DatabaseMigration databaseMigration) {
    this.serverUpgradeStatus = serverUpgradeStatus;
    this.database = database;
    this.databaseMigration = databaseMigration;
  }

  @Override
  public void define(WebService.NewController controller) {
    controller.createAction("migrate")
      .setDescription("Migrate the database to match the current version of SonarQube.<br/>" +
        "state values are:" +
        "<ul>" +
        "<li>NO_MIGRATION</li>" +
        "<li>MIGRATION_NEEDED</li>" +
        "<li>MIGRATION_STARTED</li>" +
        "<li>MIGRATION_SUCCEEDED</li>" +
        "<li>MIGRATION_FAILED</li>" +
        "</ul>")
      .setSince("5.2")
      .setPost(true)
      .setHandler(this)
      .setResponseExample(Resources.getResource(this.getClass(), "example-migrate.json"));
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    if (serverUpgradeStatus.isUpgraded()) {
      writeResponse(response, databaseMigration);
    }
    else if (!database.getDialect().supportsMigration()) {
      writeNotSupportedResponse(response);
    }
    else {
      switch (databaseMigration.status()) {
        case RUNNING:
          writeResponse(response, databaseMigration);
          break;
        case FAILED:
          writeResponse(response, databaseMigration);
          break;
        case SUCCEEDED:
          writeResponse(response, databaseMigration);
          break;
        case NONE:
          databaseMigration.startIt();
          writeResponse(response, databaseMigration);
          break;
        default:
          throw new RuntimeException(UNSUPPORTED_DATABASE_MIGRATION_STATUS);
      }
    }
  }

  private void writeNotSupportedResponse(Response response) {
    JsonWriter jsonWriter = response.newJsonWriter();
    jsonWriter.beginObject()
        .prop("operational", false)
        .prop("state", DatabaseMigration.Status.NONE.toString())
        .prop("message", "Upgrade is not supported. Please use a <a href=\\\"http://redirect.sonarsource.com/doc/requirements.html\\\">production-ready database</a>.")
        .endObject();
    jsonWriter.close();
  }

  private void writeResponse(Response response, DatabaseMigration databaseMigration) {
    JsonWriter jsonWriter = response.newJsonWriter();
    jsonWriter.beginObject()
        .prop("operational", isOperational(databaseMigration))
        .prop("state", databaseMigration.status().toString())
        .prop("message", buildMessage(databaseMigration))
        .propDateTime("startedAt", databaseMigration.startedAt())
        .endObject();
    jsonWriter.close();
  }

  private static boolean isOperational(DatabaseMigration databaseMigration) {
    return databaseMigration.status() == NONE || databaseMigration.status() == SUCCEEDED;
  }

  private static String buildMessage(DatabaseMigration databaseMigration) {
    switch (databaseMigration.status()) {
      case NONE:
        return "Database is up-to-date, no migration needed.";
      case RUNNING:
        return "Database migration is running.";
      case SUCCEEDED:
        return "Migration succeeded.";
      case FAILED:
        return "Migration failed: " + failureMessage(databaseMigration) + ".<br/> Please check logs.";
      default:
        return UNSUPPORTED_DATABASE_MIGRATION_STATUS;
    }
  }

  private static String failureMessage(DatabaseMigration databaseMigration) {
    Throwable failureError = databaseMigration.failureError();
    if (failureError == null) {
      return "No failure error";
    }
    return failureError.getMessage();
  }

}
