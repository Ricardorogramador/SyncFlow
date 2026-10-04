package com.example.pasaas2.service;
import com.example.pasaas2.dto.EventRequest;
import com.example.pasaas2.dto.GeminiRequest;
import com.example.pasaas2.dto.GeminiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class GeminiAiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GeminiAiService(
            @Value("${api.gemini.url}") String apiUrl,
            @Value("${api.gemini.key}") String apiKey,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(apiUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public EventRequest parseEventFromText(String userPrompt) {
        LocalDateTime now = LocalDateTime.now();
        String currentDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String dayOfWeek = now.getDayOfWeek().toString();

        String systemPrompt = String.format("""
            Eres un asistente de calendario estricto. Tu única tarea es extraer la información del evento del texto del usuario.
            Hoy es %s, %s.
            
            Reglas estrictas:
            1. Asume que la zona horaria es local.
            2. Si no se especifica la duración, asume 1 hora.
            3. Responde ÚNICAMENTE con un objeto JSON válido. Nada de texto adicional.
            
            El JSON debe tener exactamente esta estructura:
            {
              "title": "String (resumen corto del evento)",
              "description": "String (detalles adicionales, o null si no hay)",
              "startTime": "YYYY-MM-DDTHH:mm:ss",
              "endTime": "YYYY-MM-DDTHH:mm:ss"
            }
            
            Texto del usuario: "%s"
            """, dayOfWeek, currentDateTime, userPrompt);

        GeminiRequest requestPayload = GeminiRequest.builder()
                .contents(List.of(GeminiRequest.Content.builder()
                        .parts(List.of(GeminiRequest.Part.builder().text(systemPrompt).build()))
                        .build()))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .responseMimeType("application/json")
                        .build())
                .build();

        GeminiResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("key", apiKey).build())
                .body(requestPayload)
                .retrieve()
                .body(GeminiResponse.class);

        try {
            String jsonOutput = response.getCandidates().get(0).getContent().getParts().get(0).getText();
            return objectMapper.readValue(jsonOutput, EventRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar la respuesta de la IA: " + e.getMessage());
        }
    }
}
