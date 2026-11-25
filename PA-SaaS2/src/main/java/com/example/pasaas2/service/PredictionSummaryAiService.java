package com.example.pasaas2.service;

import com.example.pasaas2.dto.GeminiRequest;
import com.example.pasaas2.dto.GeminiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class PredictionSummaryAiService {

    private final RestClient restClient;
    private final String apiKey;

    public PredictionSummaryAiService(
            @Value("${api.gemini.url}") String apiUrl,
            @Value("${api.gemini.key}") String apiKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
        this.apiKey = apiKey;
    }

    public String summarize(
            Map<String, Object> aggregatedReport
    ) {
        String prompt = """
                Eres un analista de resultados de riesgo de agenda.
                Explica brevemente los resultados en español.
                Basa todas las afirmaciones exclusivamente en las métricas
                numéricas incluidas abajo. No inventes causas, porcentajes,
                tendencias ni recomendaciones que no se deduzcan de esos datos.
                Indica que la probabilidad es una estimación del modelo y que
                los resultados requieren validación con datos reales.
                No pidas ni infieras nombres o datos personales.

                Métricas agregadas:
                %s
                """.formatted(aggregatedReport);

        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(
                                        GeminiRequest.Part.builder()
                                                .text(prompt)
                                                .build()
                                ))
                                .build()
                ))
                .generationConfig(
                        GeminiRequest.GenerationConfig.builder()
                                .responseMimeType("text/plain")
                                .build()
                )
                .build();

        GeminiResponse response = restClient.post()
                .uri(uriBuilder ->
                        uriBuilder
                                .queryParam("key", apiKey)
                                .build()
                )
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        if (response == null ||
                response.getCandidates() == null ||
                response.getCandidates().isEmpty() ||
                response.getCandidates().get(0).getContent() == null ||
                response.getCandidates().get(0).getContent().getParts() == null ||
                response.getCandidates().get(0).getContent().getParts().isEmpty()) {
            throw new IllegalStateException(
                    "Gemini devolvió una respuesta vacía"
            );
        }

        return response.getCandidates()
                .get(0)
                .getContent()
                .getParts()
                .get(0)
                .getText();
    }
}