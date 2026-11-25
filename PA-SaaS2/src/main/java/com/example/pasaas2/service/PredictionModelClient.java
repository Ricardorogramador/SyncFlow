package com.example.pasaas2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class PredictionModelClient {

    private static final long MAX_FILE_BYTES =
            10L * 1024L * 1024L;
    private static final Logger log =
            LoggerFactory.getLogger(PredictionModelClient.class);

    private final RestClient restClient;



    public PredictionModelClient(
            @Value("${prediction.service.url:http://prediction-service:8000}")
            String predictionServiceUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.restClient = RestClient.builder()
                .baseUrl(predictionServiceUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    public Map<String, Object> train(
            UUID organizationId,
            MultipartFile file
    ) {
        return postCsv(
                organizationId,
                file,
                "/models/{organizationId}/train"
        );
    }

    public Map<String, Object> predict(
            UUID organizationId,
            MultipartFile file
    ) {
        return postCsv(
                organizationId,
                file,
                "/models/{organizationId}/predict"
        );
    }

    private Map<String, Object> postCsv(
            UUID organizationId,
            MultipartFile file,
            String path
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona un archivo CSV"
            );
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "El CSV supera el límite de 10 MB"
            );
        }

        String filename = file.getOriginalFilename();

        if (filename == null ||
                !filename.toLowerCase().endsWith(".csv")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El archivo debe tener extensión .csv"
            );
        }

        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return "upload.csv";
                }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", resource)
                    .filename("upload.csv")
                    .contentType(MediaType.parseMediaType("text/csv"));

            return restClient.post()
                    .uri(path, organizationId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        } catch (RestClientResponseException ex) {
            log.error("Falló la llamada al servicio predictivo: {}",
                    ex.getResponseBodyAsString(), ex);
            if (ex.getStatusCode().value() == 409) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Primero entrena el modelo de esta organización con un CSV etiquetado"
                );
            }

            if (ex.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(
                        ex.getStatusCode(),
                        "El CSV no cumple el formato requerido"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "El servicio predictivo no pudo procesar el archivo"
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se pudo leer el archivo CSV",
                    ex
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "El servicio predictivo no está disponible",
                    ex
            );
        }
    }
}