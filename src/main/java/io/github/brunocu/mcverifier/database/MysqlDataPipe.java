package io.github.brunocu.mcverifier.database;

import java.sql.DriverManager;
import java.sql.SQLException;

public class MysqlDataPipe extends DataPipe {

    public MysqlDataPipe(String host, String port, String database, String user, String password, String tablePrefix) throws SQLException {
        String url = String.format("jdbc:mysql://%s:%s/%s",
                host, port, database);
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            tableName = tablePrefix + tableName;
        }
        this.conn = DriverManager.getConnection(url, user, password);
        initSchema();
    }
}
