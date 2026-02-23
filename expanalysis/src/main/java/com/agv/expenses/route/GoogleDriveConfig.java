package com.agv.expenses.route;

import java.io.InputStream;
import java.util.Collections;

import org.apache.camel.component.google.drive.BatchGoogleDriveClientFactory;
import org.apache.camel.component.google.drive.GoogleDriveClientFactory;
import org.apache.camel.component.google.drive.GoogleDriveComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

@Configuration
public class GoogleDriveConfig {

    @Bean
    public GoogleDriveClientFactory googleDriveClientFactory() {
        return new BatchGoogleDriveClientFactory();
    }

    @Bean
    public Drive driveService() throws Exception {
        InputStream in = getClass().getResourceAsStream("/credentials.json");
        if (in == null) {
            throw new RuntimeException("credentials.json not found in classpath");
        }

        // The modern way to load Service Account credentials
        GoogleCredentials credentials = ServiceAccountCredentials.fromStream(in)
                .createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)) // Required for modern Auth
                .setApplicationName("expense-analytics-sheets")
                .build();
    }
}