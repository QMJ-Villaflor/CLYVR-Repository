package clyvr.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnectionTest {

    public static void main(String[] args) {
        try (
            Connection connection = Database.getConnection();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "SELECT current_database(), current_timestamp"
            )
        ) {
            if (result.next()) {
                System.out.println("Supabase connection successful!");
                System.out.println("Database: " + result.getString(1));
                System.out.println("Server time: " + result.getString(2));
            }
        } catch (SQLException e) {
            System.err.println("Connection test failed.");

            SQLException current = e;

            while (current != null) {
                System.err.println(
                    "Exception: " + current.getClass().getSimpleName()
                );
                System.err.println("SQL State: " + current.getSQLState());
                System.err.println("Error Code: " + current.getErrorCode());
                System.err.println("Message: " + current.getMessage());

                Throwable cause = current.getCause();

                while (cause != null) {
                    System.err.println(
                        "Cause: " + cause.getClass().getSimpleName()
                    );
                    System.err.println("Cause Message: " + cause.getMessage());
                    cause = cause.getCause();
                }

                current = current.getNextException();
            }

            System.exit(1);
        }
    }
}