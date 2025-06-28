package com.example.acid_demo.controller;

import com.example.acid_demo.entity.Account;
import com.example.acid_demo.entity.TransactionLog;
import com.example.acid_demo.repository.AccountRepository;
import com.example.acid_demo.repository.TransactionLogRepository;
import com.example.acid_demo.service.TransferService;
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
    
    /**
     * Создание тестовых счетов
     */
    @PostMapping("/accounts/init")
    public ResponseEntity<String> initAccounts() {
        accountRepository.deleteAll();
        transactionLogRepository.deleteAll();
        
        accountRepository.save(new Account("ACC001", new BigDecimal("1000.00")));
        accountRepository.save(new Account("ACC002", new BigDecimal("500.00")));
        accountRepository.save(new Account("ACC003", new BigDecimal("750.00")));
        
        return ResponseEntity.ok("Созданы 3 тестовых счёта");
    }
    
    /**
     * Получить все счета
     */
    @GetMapping("/accounts")
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }
    
    /**
     * Получить историю транзакций
     */
    @GetMapping("/transactions")
    public List<TransactionLog> getTransactions() {
        return transactionLogRepository.findAll();
    }
    
    /**
     * Демонстрация АТОМАРНОСТИ - успешный перевод
     */
    @PostMapping("/transfer/atomic")
    public ResponseEntity<Map<String, Object>> atomicTransfer(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        
        try {
            transferService.transferMoney(from, to, amount);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Перевод выполнен атомарно"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage(),
                    "note", "Транзакция откатилась, состояние счетов не изменилось"
            ));
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
        
        try {
            transferService.transferMoneyWithoutTransaction(from, to, amount, simulateError);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Перевод выполнен БЕЗ транзакции"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage(),
                    "warning", "ВНИМАНИЕ! Деньги могли быть списаны, но не зачислены!"
            ));
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
        
        return ResponseEntity.ok(Map.of(
                "transfer1", result1,
                "transfer2", result2,
                "note", "При оптимистичной блокировке одна из транзакций может не выполниться из-за конфликта версий"
        ));
    }
    
    /**
     * Демонстрация пессимистичной блокировки
     */
    @PostMapping("/transfer/pessimistic-lock")
    public ResponseEntity<Map<String, Object>> pessimisticLockDemo(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        
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
        
        return ResponseEntity.ok(Map.of(
                "transfer1", result1,
                "transfer2", result2,
                "duration", duration + "ms",
                "note", "При пессимистичной блокировке транзакции выполняются последовательно"
        ));
    }
} 