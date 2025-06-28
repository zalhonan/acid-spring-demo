package com.example.acid_demo.controller;

import com.example.acid_demo.entity.Account;
import com.example.acid_demo.entity.TransactionLog;
import com.example.acid_demo.repository.AccountRepository;
import com.example.acid_demo.repository.TransactionLogRepository;
import com.example.acid_demo.service.TransferService;
import com.example.acid_demo.util.JsonLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/acid")
@RequiredArgsConstructor
@Slf4j
public class AcidDemoController {
    
    private final TransferService transferService;
    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final JsonLogger jsonLogger;
    
    /**
     * Создание тестовых счетов
     */
    @PostMapping("/accounts/init")
    public ResponseEntity<String> initAccounts() {
        jsonLogger.logOperation("ИНИЦИАЛИЗАЦИЯ ТЕСТОВЫХ ДАННЫХ", Map.of(
            "операция", "Создание тестовых счетов"
        ));
        
        accountRepository.deleteAll();
        transactionLogRepository.deleteAll();
        
        Account acc1 = accountRepository.save(new Account("ACC001", new BigDecimal("1000.00")));
        Account acc2 = accountRepository.save(new Account("ACC002", new BigDecimal("500.00")));
        Account acc3 = accountRepository.save(new Account("ACC003", new BigDecimal("750.00")));
        
        jsonLogger.logInfo("Тестовые счета созданы", Map.of(
            "счета", List.of(
                Map.of("номер", acc1.getAccountNumber(), "баланс", acc1.getBalance()),
                Map.of("номер", acc2.getAccountNumber(), "баланс", acc2.getBalance()),
                Map.of("номер", acc3.getAccountNumber(), "баланс", acc3.getBalance())
            ),
            "общая_сумма", new BigDecimal("2250.00")
        ));
        
        return ResponseEntity.ok("Созданы 3 тестовых счёта");
    }
    
    /**
     * Получить все счета
     */
    @GetMapping("/accounts")
    public List<Account> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();
        
        jsonLogger.logInfo("Запрос всех счетов", Map.of(
            "количество", accounts.size(),
            "счета", accounts.stream().map(acc -> Map.of(
                "номер", acc.getAccountNumber(),
                "баланс", acc.getBalance(),
                "версия", acc.getVersion()
            )).toList()
        ));
        
        return accounts;
    }
    
    /**
     * Получить историю транзакций
     */
    @GetMapping("/transactions")
    public List<TransactionLog> getTransactions() {
        List<TransactionLog> transactions = transactionLogRepository.findAll();
        
        jsonLogger.logInfo("Запрос истории транзакций", Map.of(
            "количество", transactions.size(),
            "успешных", transactions.stream()
                .filter(t -> t.getStatus() == TransactionLog.TransactionStatus.SUCCESS)
                .count(),
            "неудачных", transactions.stream()
                .filter(t -> t.getStatus() == TransactionLog.TransactionStatus.FAILED)
                .count()
        ));
        
        return transactions;
    }
    
    /**
     * Демонстрация АТОМАРНОСТИ - успешный перевод
     */
    @PostMapping("/transfer/atomic")
    public ResponseEntity<Map<String, Object>> atomicTransfer(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        
        jsonLogger.logOperation("API: АТОМАРНЫЙ ПЕРЕВОД", Map.of(
            "endpoint", "/transfer/atomic",
            "от", from,
            "кому", to,
            "сумма", amount
        ));
        
        try {
            transferService.transferMoney(from, to, amount);
            
            Map<String, Object> response = Map.of(
                    "status", "SUCCESS",
                    "message", "Перевод выполнен атомарно",
                    "демонстрация", "ATOMICITY",
                    "результат", "Все операции выполнены успешно"
            );
            
            jsonLogger.logInfo("Атомарный перевод успешен", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "status", "FAILED",
                    "message", e.getMessage(),
                    "демонстрация", "ATOMICITY",
                    "note", "Транзакция откатилась, состояние счетов не изменилось"
            );
            
            jsonLogger.logError("Атомарный перевод отменён", response);
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Демонстрация нарушения АТОМАРНОСТИ - перевод без транзакции
     */
    @PostMapping("/transfer/non-atomic")
    public ResponseEntity<Map<String, Object>> nonAtomicTransfer(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "false") boolean simulateError) {
        
        jsonLogger.logOperation("API: НЕАТОМАРНЫЙ ПЕРЕВОД", Map.of(
            "endpoint", "/transfer/non-atomic",
            "от", from,
            "кому", to,
            "сумма", amount,
            "симуляция_ошибки", simulateError,
            "ВНИМАНИЕ", "БЕЗ транзакции!"
        ));
        
        try {
            transferService.transferMoneyWithoutTransaction(from, to, amount, simulateError);
            
            Map<String, Object> response = Map.of(
                    "status", "SUCCESS",
                    "message", "Перевод выполнен БЕЗ транзакции",
                    "демонстрация", "НАРУШЕНИЕ ATOMICITY"
            );
            
            jsonLogger.logInfo("Неатомарный перевод завершён", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                    "status", "FAILED",
                    "message", e.getMessage(),
                    "демонстрация", "НАРУШЕНИЕ ATOMICITY",
                    "warning", "ВНИМАНИЕ! Деньги могли быть списаны, но не зачислены!",
                    "критично", "Нарушена целостность данных"
            );
            
            jsonLogger.logError("Неатомарный перевод прерван", response);
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Демонстрация оптимистичной блокировки
     */
    @PostMapping("/transfer/optimistic-lock")
    public ResponseEntity<Map<String, Object>> optimisticLockDemo(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        
        jsonLogger.logOperation("API: ДЕМОНСТРАЦИЯ ОПТИМИСТИЧНОЙ БЛОКИРОВКИ", Map.of(
            "endpoint", "/transfer/optimistic-lock",
            "описание", "Запуск двух параллельных переводов"
        ));
        
        // Запускаем два параллельных перевода для демонстрации конфликта
        CompletableFuture<String> transfer1 = CompletableFuture.supplyAsync(() -> {
            try {
                transferService.transferWithOptimisticLock(from, to, amount);
                return "SUCCESS";
            } catch (Exception e) {
                return "FAILED: " + e.getMessage();
            }
        });
        
        CompletableFuture<String> transfer2 = CompletableFuture.supplyAsync(() -> {
            try {
                transferService.transferWithOptimisticLock(to, from, amount.divide(new BigDecimal(2)));
                return "SUCCESS";
            } catch (Exception e) {
                return "FAILED: " + e.getMessage();
            }
        });
        
        String result1 = transfer1.join();
        String result2 = transfer2.join();
        
        Map<String, Object> response = Map.of(
                "transfer1", Map.of(
                    "направление", from + " → " + to,
                    "сумма", amount,
                    "результат", result1
                ),
                "transfer2", Map.of(
                    "направление", to + " → " + from,
                    "сумма", amount.divide(new BigDecimal(2)),
                    "результат", result2
                ),
                "тип_блокировки", "OPTIMISTIC",
                "note", "При оптимистичной блокировке одна из транзакций может не выполниться из-за конфликта версий"
        );
        
        jsonLogger.logInfo("Результаты оптимистичной блокировки", response);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Демонстрация пессимистичной блокировки
     */
    @PostMapping("/transfer/pessimistic-lock")
    public ResponseEntity<Map<String, Object>> pessimisticLockDemo(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        
        jsonLogger.logOperation("API: ДЕМОНСТРАЦИЯ ПЕССИМИСТИЧНОЙ БЛОКИРОВКИ", Map.of(
            "endpoint", "/transfer/pessimistic-lock",
            "описание", "Запуск двух параллельных переводов с блокировкой"
        ));
        
        long startTime = System.currentTimeMillis();
        
        // Запускаем два параллельных перевода
        CompletableFuture<String> transfer1 = CompletableFuture.supplyAsync(() -> {
            try {
                transferService.transferWithPessimisticLock(from, to, amount);
                return "SUCCESS";
            } catch (Exception e) {
                return "FAILED: " + e.getMessage();
            }
        });
        
        CompletableFuture<String> transfer2 = CompletableFuture.supplyAsync(() -> {
            try {
                // Небольшая задержка чтобы второй перевод начался после первого
                Thread.sleep(50);
                transferService.transferWithPessimisticLock(to, from, amount.divide(new BigDecimal(2)));
                return "SUCCESS";
            } catch (Exception e) {
                return "FAILED: " + e.getMessage();
            }
        });
        
        String result1 = transfer1.join();
        String result2 = transfer2.join();
        
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = Map.of(
                "transfer1", Map.of(
                    "направление", from + " → " + to,
                    "сумма", amount,
                    "результат", result1
                ),
                "transfer2", Map.of(
                    "направление", to + " → " + from,
                    "сумма", amount.divide(new BigDecimal(2)),
                    "результат", result2
                ),
                "тип_блокировки", "PESSIMISTIC",
                "duration", duration + "ms",
                "note", "При пессимистичной блокировке транзакции выполняются последовательно"
        );
        
        jsonLogger.logInfo("Результаты пессимистичной блокировки", response);
        
        return ResponseEntity.ok(response);
    }
} 