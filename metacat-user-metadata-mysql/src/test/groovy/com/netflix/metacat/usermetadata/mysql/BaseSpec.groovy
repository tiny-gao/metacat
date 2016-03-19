package com.netflix.metacat.usermetadata.mysql

import com.google.inject.Inject
import com.netflix.metacat.common.server.CommonModule
import io.airlift.testing.mysql.TestingMySqlServer
import spock.guice.UseModules
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.atomic.AtomicBoolean

import static java.lang.String.format

@UseModules([
        CommonModule.class,
        MysqlUserMetadataModule.class,
])
@Ignore
class BaseSpec extends Specification {
    private static final AtomicBoolean initialized = new AtomicBoolean();
    @Shared
    TestingMySqlServer mysqlServer;
    @Inject
    @Shared
    MysqlUserMetadataService mysqlUserMetadataService;

    def setupSpec() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        setupMysql()
        mysqlUserMetadataService.start()
    }

    def setupMysql() {
        mysqlServer = new TestingMySqlServer("test", "test", "metacat")
        Properties props = new Properties()
        props.setProperty('javax.jdo.option.url', format("jdbc:mysql://localhost:%d/%s?user=%s&password=%s", mysqlServer.port, "metacat", mysqlServer.user, mysqlServer.password))
        props.setProperty('javax.jdo.option.username', mysqlServer.getUser())
        props.setProperty('javax.jdo.option.password', mysqlServer.getPassword())
        props.setProperty('javax.jdo.option.defaultTransactionIsolation','READ_COMMITTED')
        props.setProperty('javax.jdo.option.defaultAutoCommit', 'false');
        URL url = Thread.currentThread().getContextClassLoader().getResource("usermetadata.properties")
        Path filePath
        if( url != null) {
            filePath = Paths.get(url.toURI());
        } else {
            File metadataFile = new File('src/test/resources/usermetadata.properties')
            if( !metadataFile.exists()){
                metadataFile = new File('metacat-user-metadata-mysql/src/test/resources/usermetadata.properties')
            }
            filePath = Paths.get(metadataFile.getPath())
        }
        props.store(Files.newOutputStream(filePath), "test")

        File prepareFile = new File('src/test/resources/sql/prepare-test.sql')
        if( !prepareFile.exists()){
            prepareFile = new File('metacat-user-metadata-mysql/src/test/resources/sql/prepare-test.sql')
        }
        runScript(DriverManager.getConnection(mysqlServer.getJdbcUrl()), new FileReader(prepareFile), ';')
    }

    def runScript(Connection conn, Reader reader, String delimiter) throws IOException,
            SQLException {
        StringBuffer command = null;
        try {
            LineNumberReader lineReader = new LineNumberReader(reader);
            String line = null;
            while ((line = lineReader.readLine()) != null) {
                if (command == null) {
                    command = new StringBuffer();
                }
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("--")) {
                    println(trimmedLine);
                } else if (trimmedLine.length() < 1
                        || trimmedLine.startsWith("//")) {
                    // Do nothing
                } else if (trimmedLine.length() < 1
                        || trimmedLine.startsWith("--")) {
                    // Do nothing
                } else if (trimmedLine.endsWith(delimiter)) {
                    command.append(line.substring(0, line
                            .lastIndexOf(delimiter)));
                    command.append(" ");
                    Statement statement = conn.createStatement();

                    println(command);
                    statement.execute(command.toString());

                    command = null;
                    try {
                        statement.close();
                    } catch (Exception e) {
                        // Ignore to workaround a bug in Jakarta DBCP
                    }
                    Thread.yield();
                } else {
                    command.append(line);
                    command.append(" ");
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    def cleanupSpec() {
        if (mysqlServer != null) {
            mysqlServer.close()
        }
    }
}