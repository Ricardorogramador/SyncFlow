package com.example.pasaas2.service;

import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.MembershipRole;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PredictiveModelService {

    private final OrganizationService organizationService;
    private final PredictionModelClient modelClient;
    private final PredictionSummaryAiService summaryAiService;

    public Map<String, Object> train(
            UUID organizationId,
            MultipartFile file,
            User currentUser
    ) {
        requireOwner(organizationId, currentUser);

        Map<String, Object> result =
                new LinkedHashMap<>(
                        modelClient.train(
                                organizationId,
                                file
                        )
                );

        result.put(
                "aiAnalysis",
                createAnalysis(result)
        );

        return result;
    }

    public Map<String, Object> predict(
            UUID organizationId,
            MultipartFile file,
            User currentUser
    ) {
        requireOwner(organizationId, currentUser);

        Map<String, Object> result =
                new LinkedHashMap<>(
                        modelClient.predict(
                                organizationId,
                                file
                        )
                );

        result.put(
                "aiAnalysis",
                createAnalysis(result)
        );

        return result;
    }

    private void requireOwner(
            UUID organizationId,
            User currentUser
    ) {
        Membership membership =
                organizationService.getCurrentMembership(
                        organizationId,
                        currentUser
                );

        if (membership.getRole() != MembershipRole.OWNER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo el propietario puede utilizar el módulo predictivo"
            );
        }
    }

    private String createAnalysis(
            Map<String, Object> result
    ) {
        Map<String, Object> safeSummary =
                new LinkedHashMap<>();

        safeSummary.put(
                "selectedModel",
                result.get("selectedModel")
        );
        safeSummary.put(
                "model",
                result.get("model")
        );
        safeSummary.put(
                "trainingRows",
                result.get("trainingRows")
        );
        safeSummary.put(
                "testRows",
                result.get("testRows")
        );
        safeSummary.put(
                "totalRows",
                result.get("totalRows")
        );
        safeSummary.put(
                "metrics",
                result.get("metrics")
        );
        safeSummary.put(
                "riskCounts",
                result.get("riskCounts")
        );

        try {
            return summaryAiService.summarize(
                    safeSummary
            );
        } catch (RestClientException |
                 IllegalStateException ex) {
            return "Las predicciones se calcularon, pero el análisis de IA no está disponible en este momento.";
        }
    }
}