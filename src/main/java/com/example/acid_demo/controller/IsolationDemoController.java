package com.example.acid_demo.controller;

import com.example.acid_demo.service.IsolationDemoService;
import com.example.acid_demo.util.JsonLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/isolation")
@RequiredArgsConstructor
@Slf4j
public class IsolationDemoController {
    
    private final IsolationDemoService isolationDemoService;
    private final JsonLogger jsonLogger;
    
    /**
     * Демонстрация READ UNCOMMITTED (dirty read)
     * Запустите этот эндпоинт и параллельно /api/isolation/long-update
     */
    @GetMapping("/read-uncommitted/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateReadUncommitted(@PathVariable String accountNumber) {
        jsonLogger.logOperation("API: Демонстрация READ UNCOMMITTED", Map.of(
            "endpoint", "/read-uncommitted/" + accountNumber,
            "описание", "Демонстрация грязного чтения"
        ));
        
        BigDecimal finalBalance = isolationDemoService.readUncommitted(accountNumber);
        
        Map<String, Object> response = Map.of(
                "isolation_level", "READ_UNCOMMITTED",
                "final_balance", finalBalance,
                "description", "Может прочитать незакоммиченные изменения других транзакций (dirty read)",
                "instruction", "Запустите /api/isolation/long-update параллельно для демонстрации"
        );
        
        jsonLogger.logInfo("Результат демонстрации READ UNCOMMITTED", response);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Демонстрация READ COMMITTED (non-repeatable read)
     */
    @GetMapping("/read-committed/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateReadCommitted(@PathVariable String accountNumber) {
        jsonLogger.logOperation("API: Демонстрация READ COMMITTED", Map.of(
            "endpoint", "/read-committed/" + accountNumber,
            "описание", "Демонстрация неповторяемого чтения"
        ));
        
        String result = isolationDemoService.demonstrateNonRepeatableRead(accountNumber);
        
        Map<String, Object> response = Map.of(
                "isolation_level", "READ_COMMITTED",
                "result", result,
                "description", "Не видит незакоммиченные изменения, но может видеть разные данные при повторном чтении",
                "instruction", "Запустите /api/isolation/update-balance параллельно для демонстрации"
        );
        
        jsonLogger.logInfo("Результат демонстрации READ COMMITTED", response);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Демонстрация REPEATABLE READ
     */
    @GetMapping("/repeatable-read/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateRepeatableRead(@PathVariable String accountNumber) {
        jsonLogger.logOperation("API: Демонстрация REPEATABLE READ", Map.of(
            "endpoint", "/repeatable-read/" + accountNumber,
            "описание", "Демонстрация повторяемого чтения"
        ));
        
        String result = isolationDemoService.demonstrateRepeatableRead(accountNumber);
        
        Map<String, Object> response = Map.of(
                "isolation_level", "REPEATABLE_READ",
                "result", result,
                "description", "Гарантирует одинаковые данные при повторном чтении в рамках транзакции",
                "phantom_reads", "Возможны фантомные чтения (новые записи)"
        );
        
        jsonLogger.logInfo("Результат демонстрации REPEATABLE READ", response);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Демонстрация SERIALIZABLE
     */
    @GetMapping("/serializable")
    public ResponseEntity<Map<String, Object>> demonstrateSerializable() {
        jsonLogger.logOperation("API: Демонстрация SERIALIZABLE", Map.of(
            "endpoint", "/serializable",
            "описание", "Демонстрация полной изоляции"
        ));
        
        String result = isolationDemoService.demonstrateSerializable();
        
        Map<String, Object> response = Map.of(
                "isolation_level", "SERIALIZABLE",
                "result", result,
                "description", "Полная изоляция транзакций, выполняются последовательно"
        );
        
        jsonLogger.logInfo("Результат демонстрации SERIALIZABLE", response);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Вспомогательный эндпоинт для изменения баланса
     */
    @PostMapping("/update-balance/{accountNumber}")
    public ResponseEntity<String> updateBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        
        jsonLogger.logInfo("API: Изменение баланса", Map.of(
            "endpoint", "/update-balance/" + accountNumber,
            "сумма", amount
        ));
        
        isolationDemoService.updateBalance(accountNumber, amount);
        return ResponseEntity.ok("Баланс изменён на " + amount);
    }
    
    /**
     * Вспомогательный эндпоинт для долгой транзакции
     */
    @PostMapping("/long-update/{accountNumber}")
    public ResponseEntity<String> longUpdate(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        
        jsonLogger.logInfo("API: Запуск долгой транзакции", Map.of(
            "endpoint", "/long-update/" + accountNumber,
            "сумма", amount,
            "длительность", "5 секунд"
        ));
        
        isolationDemoService.longRunningUpdate(accountNumber, amount);
        return ResponseEntity.ok("Долгая транзакция завершена");
    }
    
    /**
     * Комплексная демонстрация всех уровней изоляции
     */
    @GetMapping("/demo-all/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateAllLevels(@PathVariable String accountNumber) {
        jsonLogger.logOperation("API: КОМПЛЕКСНАЯ ДЕМОНСТРАЦИЯ", Map.of(
            "endpoint", "/demo-all/" + accountNumber,
            "описание", "Демонстрация всех уровней изоляции"
        ));
        
        // Запускаем параллельное изменение баланса
        CompletableFuture<Void> updater = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                isolationDemoService.updateBalance(accountNumber, new BigDecimal("100"));
                jsonLogger.logInfo("Параллельное изменение выполнено", Map.of(
                    "счёт", accountNumber,
                    "изменение", "100"
                ));
            } catch (Exception e) {
                jsonLogger.logError("Ошибка при параллельном изменении", Map.of(
                    "ошибка", e.getMessage()
                ));
            }
        });
        
        // Тестируем разные уровни изоляции
        Map<String, String> results = Map.of(
                "READ_COMMITTED", isolationDemoService.demonstrateNonRepeatableRead(accountNumber),
                "REPEATABLE_READ", isolationDemoService.demonstrateRepeatableRead(accountNumber),
                "SERIALIZABLE", isolationDemoService.demonstrateSerializable()
        );
        
        updater.join();
        
        Map<String, Object> response = Map.of(
                "results", results,
                "note", "Сравните результаты для разных уровней изоляции"
        );
        
        jsonLogger.logInfo("Результаты комплексной демонстрации", response);
        
        return ResponseEntity.ok(response);
    }
} 