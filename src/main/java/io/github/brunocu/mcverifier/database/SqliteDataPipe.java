package io.github.brunocu.mcverifier.database;

import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteDataPipe extends DataPipe {
    public SqliteDataPipe(String filepath) throws SQLException {
        String url = String.format("jdbc:sqlite:%s", filepath);
        conn = DriverManager.getConnection(url);
        initSchema();
    }
}
