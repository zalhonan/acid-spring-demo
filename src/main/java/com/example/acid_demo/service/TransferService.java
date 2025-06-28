package com.example.acid_demo.service;

import com.example.acid_demo.entity.Account;
import com.example.acid_demo.entity.TransactionLog;
import com.example.acid_demo.entity.TransactionLog.TransactionStatus;
import com.example.acid_demo.repository.AccountRepository;
import com.example.acid_demo.repository.TransactionLogRepository;
import com.example.acid_demo.util.JsonLogger;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    
    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final JsonLogger jsonLogger;
    
    /**
     * Демонстрация АТОМАРНОСТИ - либо все операции выполнятся, либо ни одна
     */
    @Transactional
    public void transferMoney(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        jsonLogger.logOperation("НАЧАЛО ТРАНЗАКЦИИ ПЕРЕВОДА", Map.of(
            "от", fromAccountNumber,
            "кому", toAccountNumber,
            "сумма", amount,
            "тип", "ATOMIC",
            "время", LocalDateTime.now()
        ));
        
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setFromAccount(fromAccountNumber);
        transactionLog.setToAccount(toAccountNumber);
        transactionLog.setAmount(amount);
        transactionLog.setTimestamp(LocalDateTime.now());
        
        try {
            Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                    .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
            
            Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                    .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
            
            jsonLogger.logInfo("Состояние счетов ДО перевода", Map.of(
                "счёт_отправителя", Map.of(
                    "номер", fromAccount.getAccountNumber(),
                    "баланс", fromAccount.getBalance()
                ),
                "счёт_получателя", Map.of(
                    "номер", toAccount.getAccountNumber(),
                    "баланс", toAccount.getBalance()
                )
            ));
            
            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new RuntimeException("Недостаточно средств на счёте");
            }
            
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            toAccount.setBalance(toAccount.getBalance().add(amount));
            
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);
            
            transactionLog.setStatus(TransactionStatus.SUCCESS);
            transactionLogRepository.save(transactionLog);
            
            jsonLogger.logInfo("Состояние счетов ПОСЛЕ перевода", Map.of(
                "счёт_отправителя", Map.of(
                    "номер", fromAccount.getAccountNumber(),
                    "баланс", fromAccount.getBalance()
                ),
                "счёт_получателя", Map.of(
                    "номер", toAccount.getAccountNumber(),
                    "баланс", toAccount.getBalance()
                ),
                "статус", "SUCCESS"
            ));
            
        } catch (Exception e) {
            jsonLogger.logError("ОШИБКА при переводе", Map.of(
                "от", fromAccountNumber,
                "кому", toAccountNumber,
                "сумма", amount,
                "ошибка", e.getMessage(),
                "статус", "FAILED"
            ));
            transactionLog.setStatus(TransactionStatus.FAILED);
            transactionLog.setErrorMessage(e.getMessage());
            transactionLogRepository.save(transactionLog);
            throw e;
        }
    }
    
    /**
     * Демонстрация нарушения атомарности (БЕЗ @Transactional)
     */
    public void transferMoneyWithoutTransaction(String fromAccountNumber, String toAccountNumber, 
                                                BigDecimal amount, boolean simulateError) {
        jsonLogger.logOperation("ПЕРЕВОД БЕЗ ТРАНЗАКЦИИ", Map.of(
            "от", fromAccountNumber,
            "кому", toAccountNumber,
            "сумма", amount,
            "симуляция_ошибки", simulateError,
            "ВНИМАНИЕ", "Операция выполняется БЕЗ транзакции!"
        ));
        
        // Списываем деньги
        Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Недостаточно средств на счёте");
        }
        
        BigDecimal oldBalance = fromAccount.getBalance();
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);
        
        jsonLogger.logInfo("Деньги СПИСАНЫ", Map.of(
            "счёт", fromAccountNumber,
            "старый_баланс", oldBalance,
            "новый_баланс", fromAccount.getBalance(),
            "списано", amount
        ));
        
        // Симулируем ошибку после списания
        if (simulateError) {
            jsonLogger.logError("СИМУЛЯЦИЯ ОШИБКИ после списания", Map.of(
                "статус", "Деньги списаны, но НЕ зачислены!",
                "счёт_отправителя", fromAccountNumber,
                "потеряно", amount
            ));
            throw new RuntimeException("Ошибка после списания денег!");
        }
        
        // Зачисляем деньги
        Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
        
        BigDecimal oldToBalance = toAccount.getBalance();
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(toAccount);
        
        jsonLogger.logInfo("Деньги ЗАЧИСЛЕНЫ", Map.of(
            "счёт", toAccountNumber,
            "старый_баланс", oldToBalance,
            "новый_баланс", toAccount.getBalance(),
            "зачислено", amount
        ));
    }
    
    /**
     * Демонстрация оптимистичной блокировки
     */
    @Transactional
    public void transferWithOptimisticLock(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        jsonLogger.logOperation("ПЕРЕВОД С ОПТИМИСТИЧНОЙ БЛОКИРОВКОЙ", Map.of(
            "от", fromAccountNumber,
            "кому", toAccountNumber,
            "сумма", amount,
            "тип_блокировки", "OPTIMISTIC"
        ));
        
        Account fromAccount = accountRepository.findByAccountNumberWithOptimisticLock(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
        Account toAccount = accountRepository.findByAccountNumberWithOptimisticLock(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
        
        jsonLogger.logInfo("Версии счетов", Map.of(
            "счёт_отправителя", Map.of(
                "номер", fromAccount.getAccountNumber(),
                "версия", fromAccount.getVersion()
            ),
            "счёт_получателя", Map.of(
                "номер", toAccount.getAccountNumber(),
                "версия", toAccount.getVersion()
            )
        ));
        
        // Симулируем задержку для возможного конфликта
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Недостаточно средств на счёте");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        
        jsonLogger.logInfo("Перевод с оптимистичной блокировкой завершён", Map.of(
            "статус", "SUCCESS",
            "новые_версии", Map.of(
                fromAccount.getAccountNumber(), fromAccount.getVersion(),
                toAccount.getAccountNumber(), toAccount.getVersion()
            )
        ));
    }
    
    /**
     * Демонстрация пессимистичной блокировки
     */
    @Transactional
    public void transferWithPessimisticLock(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        jsonLogger.logOperation("ПЕРЕВОД С ПЕССИМИСТИЧНОЙ БЛОКИРОВКОЙ", Map.of(
            "от", fromAccountNumber,
            "кому", toAccountNumber,
            "сумма", amount,
            "тип_блокировки", "PESSIMISTIC_WRITE"
        ));
        
        long startTime = System.currentTimeMillis();
        
        Account fromAccount = accountRepository.findByAccountNumberWithPessimisticLock(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
        Account toAccount = accountRepository.findByAccountNumberWithPessimisticLock(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
        
        jsonLogger.logInfo("Счета ЗАБЛОКИРОВАНЫ", Map.of(
            "время_получения_блокировки_мс", System.currentTimeMillis() - startTime,
            "заблокированные_счета", List.of(fromAccountNumber, toAccountNumber)
        ));
        
        // Симулируем долгую операцию
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Недостаточно средств на счёте");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        
        jsonLogger.logInfo("Перевод с пессимистичной блокировкой завершён", Map.of(
            "статус", "SUCCESS",
            "общее_время_мс", System.currentTimeMillis() - startTime,
            "время_удержания_блокировки_мс", 500
        ));
    }
} 