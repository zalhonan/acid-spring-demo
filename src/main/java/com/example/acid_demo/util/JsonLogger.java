package com.example.acid_demo.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class JsonLogger {
    
    private final ObjectMapper objectMapper;
    
    public JsonLogger() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    public void logInfo(String message, Object data) {
        try {
            String jsonString = objectMapper.writeValueAsString(data);
            log.info("\n{}\n{}", message, jsonString);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при форматировании JSON", e);
            log.info("{}: {}", message, data);
        }
    }
    
    public void logDebug(String message, Object data) {
        try {
            String jsonString = objectMapper.writeValueAsString(data);
            log.debug("\n{}\n{}", message, jsonString);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при форматировании JSON", e);
            log.debug("{}: {}", message, data);
        }
    }
    
    public void logError(String message, Object data) {
        try {
            String jsonString = objectMapper.writeValueAsString(data);
            log.error("\n{}\n{}", message, jsonString);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при форматировании JSON", e);
            log.error("{}: {}", message, data);
        }
    }
    
    public void logOperation(String operationType, Map<String, Object> details) {
        String border = "═".repeat(60);
        String header = String.format("\n%s\n▶ %s\n%s", border, operationType, border);
        
        try {
            String jsonString = objectMapper.writeValueAsString(details);
            log.info("{}\n{}\n{}", header, jsonString, border);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при форматировании JSON", e);
            log.info("{}\n{}\n{}", header, details, border);
        }
    }
} 