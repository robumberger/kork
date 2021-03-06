/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.sql.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jooq.DSLContext;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;

import javax.sql.DataSource;
import java.io.Closeable;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

import static java.lang.String.format;
import static org.jooq.SQLDialect.H2;
import static org.jooq.conf.RenderNameStyle.AS_IS;

public class SqlTestUtil {

  public static TestDatabase initDatabase() {
    return initDatabase("jdbc:h2:mem:test");
  }

  public static TestDatabase initPreviousDatabase() {
    return initDatabase("jdbc:h2:mem:test_previous");
  }

  public static TestDatabase initDatabase(String jdbcUrl) {
    HikariConfig cpConfig = new HikariConfig();
    cpConfig.setJdbcUrl(format("%s%d", jdbcUrl, Clock.systemDefaultZone().millis()));
    cpConfig.setMaximumPoolSize(5);
    DataSource dataSource = new HikariDataSource(cpConfig);

    DefaultConfiguration config = new DefaultConfiguration();
    config.set(new DataSourceConnectionProvider(dataSource));
    config.setSQLDialect(H2);
    config.settings().withRenderNameStyle(AS_IS);

    DSLContext context = new DefaultDSLContext(config);

    Liquibase migrate;
    try {
      migrate = new Liquibase(
        "db/changelog-master.yml",
        new ClassLoaderResourceAccessor(),
        DatabaseFactory.getInstance()
          .findCorrectDatabaseImplementation(new JdbcConnection(dataSource.getConnection()))
      );
    } catch (DatabaseException | SQLException e) {
      throw new DatabaseInitializationFailed(e);
    }

    try {
      migrate.update("test");
    } catch (LiquibaseException e) {
      throw new DatabaseInitializationFailed(e);
    }

    return new TestDatabase(context, migrate);
  }

  public static void cleanupDb(TestDatabase databaseContext, List<String> tables) {
    // TODO rz - iterate over schema instead
    tables.forEach(table -> databaseContext.context.truncate(table).execute());
  }


  public static class TestDatabase implements Closeable {
    public final DSLContext context;
    public final Liquibase liquibase;

    TestDatabase(DSLContext context, Liquibase liquibase) {
      this.context = context;
      this.liquibase = liquibase;
    }

    @Override
    public void close() {
      context.close();
    }
  }

  private static class DatabaseInitializationFailed extends RuntimeException {
    DatabaseInitializationFailed(Throwable cause) {
      super(cause);
    }
  }
}
