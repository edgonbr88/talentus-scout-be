package com.talentus.scout.service.bulkimport;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GoogleSheetsService {

    @Value("${talentus.google.service-account-json:}")
    private String serviceAccountJson;

    private static final Pattern SHEET_ID_PATTERN = Pattern.compile("/d/([a-zA-Z0-9-_]+)");

    public String extractSheetId(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("El ID o URL de Google Sheet no puede estar vacío");
        }
        input = input.trim();
        Matcher matcher = SHEET_ID_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return input; // Si ya es solo el ID
    }

    public List<List<Object>> readSheetData(String sheetIdOrUrl) throws Exception {
        String sheetId = extractSheetId(sheetIdOrUrl);
        log.info("Obteniendo datos de Google Sheet: {}", sheetId);

        Sheets sheetsService = getSheetsService();
        ValueRange response = sheetsService.spreadsheets().values()
                .get(sheetId, "A1:Z1000")
                .execute();

        return response.getValues();
    }

    private Sheets getSheetsService() throws Exception {
        GoogleCredentials credentials;
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            try (InputStream is = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))) {
                credentials = GoogleCredentials.fromStream(is)
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY));
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY));
        }

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Talentus-Scout-BulkImport")
                .build();
    }
}
