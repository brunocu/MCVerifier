package io.github.brunocu.mcverifier.database;

import io.github.brunocu.mcverifier.MCVerifier;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

// I can't think of a good class name
public abstract class DataPipe {
    protected final Logger logger = MCVerifier.getPluginLogger();
    protected String tableName = "verified_users";
    protected Connection conn;
    // precompiled statements
    protected PreparedStatement insertStmt, uuidStmt, memberStmt, updateStmt, dropStmt, memberFromUuidStmt, uuidFromMemberStmt, usernameFromMemberStmt, timeStampStmt;

    protected void initSchema() throws SQLException {
        // create table if doesn't exist
        DatabaseMetaData dbmd = conn.getMetaData();
        if (!dbmd.getTables(null, null, tableName, new String[]{"TABLE"}).next()) {
            logger.info("Generating Schema");
            Statement stmt = conn.createStatement();
            String createSql = String.format(
                    "CREATE TABLE %s " +
                            "  ( " +
                            "     uuid        CHAR(36) NOT NULL PRIMARY KEY, " +
                            "     username    VARCHAR(16) NOT NULL, " +
                            "     discord_id  CHAR(18) UNIQUE, " +
                            "     verified_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP " +
                            "  );",
                    tableName
            );
            stmt.executeUpdate(createSql);
        }
        // prepared statements
        insertStmt = conn.prepareStatement(
                String.format(
                        "INSERT INTO %s " +
                                "            (uuid,username,discord_id) " +
                                "VALUES      (?,?,?);",
                        tableName
                )
        );
        uuidStmt = conn.prepareStatement(
                String.format(
                        "SELECT * " +
                                "FROM   %s " +
                                "WHERE  uuid = ? " +
                                "LIMIT 1;",
                        tableName
                )
        );
        memberStmt = conn.prepareStatement(
                String.format(
                        "SELECT * " +
                                "FROM   %s " +
                                "WHERE  discord_id = ? " +
                                "LIMIT 1;",
                        tableName
                )
        );
        updateStmt = conn.prepareStatement(
                String.format(
                        "UPDATE %s " +
                                "SET    username = ? " +
                                "WHERE  uuid = ?;",
                        tableName
                )
        );
        dropStmt = conn.prepareStatement(
                String.format(
                        "DELETE FROM %s " +
                                "WHERE  discord_id = ?;",
                        tableName
                )
        );
        memberFromUuidStmt = conn.prepareStatement(
                String.format(
                        "SELECT discord_id " +
                                "FROM   %s " +
                                "WHERE  uuid = ?;",
                        tableName
                )
        );
        uuidFromMemberStmt = conn.prepareStatement(
                String.format(
                        "SELECT uuid " +
                                "FROM   %s " +
                                "WHERE  discord_id = ?;",
                        tableName
                )
        );
        usernameFromMemberStmt = conn.prepareStatement(
                String.format(
                        "SELECT username " +
                                "FROM   %s " +
                                "WHERE  discord_id = ?; ",
                        tableName
                )
        );
        timeStampStmt = conn.prepareStatement(
                String.format(
                        "SELECT verified_on " +
                                "FROM   %s " +
                                "WHERE  discord_id = ?;",
                        tableName
                )
        );
    }

    public void close() {
        // let statements autoclose because I'm lazy
        try {
            conn.close();
        } catch (SQLException e) {
            // Ignore
        }
    }

    public boolean uuidIsIn(String uuid) throws SQLException {
        uuidStmt.setString(1, uuid);
        return uuidStmt.executeQuery().next();
    }

    public boolean memberIsIn(String discord_id) throws SQLException {
        memberStmt.setString(1, discord_id);
        return memberStmt.executeQuery().next();
    }

    public int insert(String uuid, String username, String discord_id) throws SQLException {
        insertStmt.setString(1, uuid);
        insertStmt.setString(2, username);
        insertStmt.setString(3, discord_id);
        return insertStmt.executeUpdate();
    }

    public int updateUsername(String uuid, String username) throws SQLException {
        updateStmt.setString(1, username);
        updateStmt.setString(2, uuid);
        return updateStmt.executeUpdate();
    }

    public int drop(String discord_id) throws SQLException {
        dropStmt.setString(1, discord_id);
        return dropStmt.executeUpdate();
    }

    public String getMemberIdfromUuid(String uuid) throws SQLException {
        memberFromUuidStmt.setString(1, uuid);
        ResultSet rs = memberFromUuidStmt.executeQuery();
        if (rs.next())
            return rs.getString("discord_id");
        return null;
    }

    public String getUuidFromMemberId(String discord_id) throws SQLException {
        uuidFromMemberStmt.setString(1, discord_id);
        ResultSet rs = uuidFromMemberStmt.executeQuery();
        if (rs.next())
            return rs.getString("uuid");
        else
            return null;
    }

    public String getUsernameFromMemberId(String discord_id) throws SQLException {
        usernameFromMemberStmt.setString(1, discord_id);
        ResultSet rs = usernameFromMemberStmt.executeQuery();
        if (rs.next())
            return rs.getString("username");
        else
            return null;
    }

    public long getTimeStamp(String discord_id) throws SQLException {
        timeStampStmt.setString(1, discord_id);
        ResultSet rs = timeStampStmt.executeQuery();
        if (rs.next()) {
            return rs.getTimestamp("verified_on").getTime() / 1000L;
        } else
            return 0;
    }
}
