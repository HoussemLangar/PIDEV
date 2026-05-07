import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.santea.config.DatabaseConfig;
import com.santea.service.DatabaseService;

public class TestSymptomsPage {
    public static void main(String[] args) {
        System.out.println("🔥 Testing database connection for symptoms...");
        
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        System.out.println("📍 JDBC URL: " + config.jdbcUrl());
        System.out.println("👤 Username: " + config.getUsername());
        
        try {
            DatabaseService dbService = new DatabaseService(config);
            Connection conn = dbService.getConnection();
            
            if (conn == null) {
                System.out.println("❌ Connection is null!");
                return;
            }
            
            System.out.println("✅ Database connection successful!");
            
            // Test query
            String sql = "SELECT COUNT(*) as count FROM symptomes_liste";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("✅ Symptoms in database: " + rs.getInt("count"));
                }
            }
            
            conn.close();
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
