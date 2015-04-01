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
package org.sonar.server.platform.ruby;

import org.jruby.Ruby;
import org.jruby.RubyNil;
import org.jruby.RubyRuntimeAdapter;
import org.jruby.embed.InvokeFailedException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.builtin.IRubyObject;
import org.sonar.server.db.migrations.RubyDatabaseMigration;

import java.io.IOException;
import java.io.InputStream;

public class PlatformRubyBridge implements RubyBridge {
  private static final String CALL_UPGRADE_AND_START_RB_FILENAME = "call_upgrade_and_start.rb";

  private final Ruby rubyRuntime;
  private final RubyRuntimeAdapter adapter = JavaEmbedUtils.newRuntimeAdapter();

  public PlatformRubyBridge(Ruby rubyRuntime) {
    this.rubyRuntime = rubyRuntime;
  }

  @Override
  public RubyDatabaseMigration databaseMigration() {
    final CallUpgradeAndStart callUpgradeAndStart = parseMethodScriptToInterface(
      CALL_UPGRADE_AND_START_RB_FILENAME, CallUpgradeAndStart.class
    );

    return new RubyDatabaseMigration() {
      @Override
      public void trigger() {
        callUpgradeAndStart.callUpgradeAndStart();
      }
    };
  }

  /**
   * Parses a Ruby script that defines a single method and returns an instance of the specified interface type as a
   * wrapper to this Ruby method.
   */
  private <T> T parseMethodScriptToInterface(String fileName, Class<T> clazz) {
    try (InputStream in = getClass().getResourceAsStream(fileName)) {
      JavaEmbedUtils.EvalUnit evalUnit = adapter.parse(rubyRuntime, in, fileName, 0);
      IRubyObject rubyObject = evalUnit.run();
      Object receiver = JavaEmbedUtils.rubyToJava(rubyObject);
      T wrapper = getInstance(rubyRuntime, receiver, clazz);
      return wrapper;
    } catch (IOException e) {
      throw new RuntimeException("Failed to load script " + fileName, e);
    }
  }

  /**
   * Fork of method {@link org.jruby.embed.internal.EmbedRubyInterfaceAdapterImpl#getInstance(Object, Class)}
   */
  @SuppressWarnings("unchecked")
  public <T> T getInstance(Ruby runtime, Object receiver, Class<T> clazz) {
    if (clazz == null || !clazz.isInterface()) {
      return null;
    }
    Object o;
    if (receiver == null || receiver instanceof RubyNil) {
      o = JavaEmbedUtils.rubyToJava(runtime, runtime.getTopSelf(), clazz);
    } else if (receiver instanceof IRubyObject) {
      o = JavaEmbedUtils.rubyToJava(runtime, (IRubyObject) receiver, clazz);
    } else {
      IRubyObject rubyReceiver = JavaUtil.convertJavaToRuby(runtime, receiver);
      o = JavaEmbedUtils.rubyToJava(runtime, rubyReceiver, clazz);
    }
    String name = clazz.getName();
    try {
      Class<T> c = (Class<T>) Class.forName(name, true, o.getClass().getClassLoader());
      return c.cast(o);
    } catch (ClassNotFoundException e) {
      throw new InvokeFailedException(e);
    }
  }
}
