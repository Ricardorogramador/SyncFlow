package com.example.pasaas2.controller;

import com.example.pasaas2.dto.ApiResponse;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.PredictiveModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(
        "/api/organizations/{organizationId}/predictive-model"
)
@RequiredArgsConstructor
public class PredictiveModelController {

    private final PredictiveModelService predictiveModelService;

    @PostMapping(
            value = "/train",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>>
    train(
            @PathVariable UUID organizationId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Modelo entrenado y evaluado",
                        predictiveModelService.train(
                                organizationId,
                                file,
                                currentUser
                        )
                )
        );
    }

    @PostMapping(
            value = "/predict",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>>
    predict(
            @PathVariable UUID organizationId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Predicciones calculadas",
                        predictiveModelService.predict(
                                organizationId,
                                file,
                                currentUser
                        )
                )
        );
    }
}