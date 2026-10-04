package com.example.pasaas2.dto;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class GeminiRequest {
    private List<Content> contents;
    private GenerationConfig generationConfig;

    @Data
    @Builder
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @Builder
    public static class Part {
        private String text;
    }

    @Data
    @Builder
    public static class GenerationConfig {
        private String responseMimeType;
    }
}
