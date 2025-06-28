package com.example.acid_demo.controller;

import com.example.acid_demo.service.IsolationDemoService;
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
    
    /**
     * Демонстрация READ UNCOMMITTED (dirty read)
     * Запустите этот эндпоинт и параллельно /api/isolation/long-update
     */
    @GetMapping("/read-uncommitted/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateReadUncommitted(@PathVariable String accountNumber) {
        log.info("Демонстрация READ UNCOMMITTED для счёта {}", accountNumber);
        
        BigDecimal finalBalance = isolationDemoService.readUncommitted(accountNumber);
        
        return ResponseEntity.ok(Map.of(
                "isolation_level", "READ_UNCOMMITTED",
                "final_balance", finalBalance,
                "description", "Может прочитать незакоммиченные изменения других транзакций (dirty read)",
                "instruction", "Запустите /api/isolation/long-update параллельно для демонстрации"
        ));
    }
    
    /**
     * Демонстрация READ COMMITTED (non-repeatable read)
     */
    @GetMapping("/read-committed/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateReadCommitted(@PathVariable String accountNumber) {
        log.info("Демонстрация READ COMMITTED для счёта {}", accountNumber);
        
        String result = isolationDemoService.demonstrateNonRepeatableRead(accountNumber);
        
        return ResponseEntity.ok(Map.of(
                "isolation_level", "READ_COMMITTED",
                "result", result,
                "description", "Не видит незакоммиченные изменения, но может видеть разные данные при повторном чтении",
                "instruction", "Запустите /api/isolation/update-balance параллельно для демонстрации"
        ));
    }
    
    /**
     * Демонстрация REPEATABLE READ
     */
    @GetMapping("/repeatable-read/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateRepeatableRead(@PathVariable String accountNumber) {
        log.info("Демонстрация REPEATABLE READ для счёта {}", accountNumber);
        
        String result = isolationDemoService.demonstrateRepeatableRead(accountNumber);
        
        return ResponseEntity.ok(Map.of(
                "isolation_level", "REPEATABLE_READ",
                "result", result,
                "description", "Гарантирует одинаковые данные при повторном чтении в рамках транзакции",
                "phantom_reads", "Возможны фантомные чтения (новые записи)"
        ));
    }
    
    /**
     * Демонстрация SERIALIZABLE
     */
    @GetMapping("/serializable")
    public ResponseEntity<Map<String, Object>> demonstrateSerializable() {
        log.info("Демонстрация SERIALIZABLE");
        
        String result = isolationDemoService.demonstrateSerializable();
        
        return ResponseEntity.ok(Map.of(
                "isolation_level", "SERIALIZABLE",
                "result", result,
                "description", "Полная изоляция транзакций, выполняются последовательно"
        ));
    }
    
    /**
     * Вспомогательный эндпоинт для изменения баланса
     */
    @PostMapping("/update-balance/{accountNumber}")
    public ResponseEntity<String> updateBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {
        
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
        
        isolationDemoService.longRunningUpdate(accountNumber, amount);
        return ResponseEntity.ok("Долгая транзакция завершена");
    }
    
    /**
     * Комплексная демонстрация всех уровней изоляции
     */
    @GetMapping("/demo-all/{accountNumber}")
    public ResponseEntity<Map<String, Object>> demonstrateAllLevels(@PathVariable String accountNumber) {
        log.info("Запуск демонстрации всех уровней изоляции");
        
        // Запускаем параллельное изменение баланса
        CompletableFuture<Void> updater = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                isolationDemoService.updateBalance(accountNumber, new BigDecimal("100"));
                log.info("Баланс изменён во время демонстрации");
            } catch (Exception e) {
                log.error("Ошибка при изменении баланса", e);
            }
        });
        
        // Тестируем разные уровни изоляции
        Map<String, String> results = Map.of(
                "READ_COMMITTED", isolationDemoService.demonstrateNonRepeatableRead(accountNumber),
                "REPEATABLE_READ", isolationDemoService.demonstrateRepeatableRead(accountNumber),
                "SERIALIZABLE", isolationDemoService.demonstrateSerializable()
        );
        
        updater.join();
        
        return ResponseEntity.ok(Map.of(
                "results", results,
                "note", "Сравните результаты для разных уровней изоляции"
        ));
    }
} 