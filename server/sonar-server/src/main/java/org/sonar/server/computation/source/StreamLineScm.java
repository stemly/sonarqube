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

package org.sonar.server.computation.source;

import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.server.source.db.FileSourceDb;

public class StreamLineScm implements StreamLine {

  private final BatchReport.Scm scmReport;

  public StreamLineScm(BatchReport.Scm scmReport) {
    this.scmReport = scmReport;
  }

  @Override
  public void readLine(int line, FileSourceDb.Line.Builder lineBuilder) {
    int changeSetIndex = scmReport.getChangesetIndexByLine(line);
    BatchReport.Scm.Changeset changeset = scmReport.getChangeset(changeSetIndex);
    if (changeset.hasAuthor()) {
      lineBuilder.setScmAuthor(changeset.getAuthor());
    }
    if (changeset.hasRevision()) {
      lineBuilder.setScmRevision(changeset.getRevision());
    }
    if (changeset.hasDate()) {
      lineBuilder.setScmDate(changeset.getDate());
    }
  }

}