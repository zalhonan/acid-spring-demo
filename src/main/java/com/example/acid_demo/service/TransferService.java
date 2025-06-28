package com.example.acid_demo.service;

import com.example.acid_demo.entity.Account;
import com.example.acid_demo.entity.TransactionLog;
import com.example.acid_demo.entity.TransactionLog.TransactionStatus;
import com.example.acid_demo.repository.AccountRepository;
import com.example.acid_demo.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    
    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * Демонстрация АТОМАРНОСТИ - либо все операции выполнятся, либо ни одна
     */
    @Transactional
    public void transferMoney(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        log.info("Начинаю перевод {} от {} к {}", amount, fromAccountNumber, toAccountNumber);
        
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setFromAccount(fromAccountNumber);
        transactionLog.setToAccount(toAccountNumber);
        transactionLog.setAmount(amount);
        
        try {
            Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                    .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
            
            Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                    .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
            
            // Проверка достаточности средств
            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new RuntimeException("Недостаточно средств на счёте");
            }
            
            // Выполняем перевод
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            toAccount.setBalance(toAccount.getBalance().add(amount));
            
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);
            
            transactionLog.setStatus(TransactionStatus.SUCCESS);
            transactionLogRepository.save(transactionLog);
            
            log.info("Перевод успешно завершён");
            
        } catch (Exception e) {
            log.error("Ошибка при переводе: {}", e.getMessage());
            transactionLog.setStatus(TransactionStatus.FAILED);
            transactionLog.setErrorMessage(e.getMessage());
            transactionLogRepository.save(transactionLog);
            throw e; // откат транзакции
        }
    }
    
    /**
     * Демонстрация нарушения атомарности (БЕЗ @Transactional)
     */
    public void transferMoneyWithoutTransaction(String fromAccountNumber, String toAccountNumber, BigDecimal amount, boolean simulateError) {
        log.info("Перевод БЕЗ транзакции {} от {} к {}", amount, fromAccountNumber, toAccountNumber);
        
        Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
        
        Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
        
        // Списываем деньги
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);
        log.info("Деньги списаны со счёта {}", fromAccountNumber);
        
        // Симулируем ошибку
        if (simulateError) {
            throw new RuntimeException("Ошибка после списания денег!");
        }
        
        // Зачисляем деньги
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(toAccount);
        log.info("Деньги зачислены на счёт {}", toAccountNumber);
    }
    
    /**
     * Демонстрация оптимистичной блокировки
     */
    @Transactional
    public void transferWithOptimisticLock(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        Account fromAccount = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
        
        Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
        
        // Эмуляция задержки для демонстрации конкурентного доступа
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }
    
    /**
     * Демонстрация пессимистичной блокировки
     */
    @Transactional
    public void transferWithPessimisticLock(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        Account fromAccount = accountRepository.findByAccountNumberWithPessimisticLock(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт отправителя не найден"));
        
        Account toAccount = accountRepository.findByAccountNumberWithPessimisticLock(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт получателя не найден"));
        
        // Эмуляция долгой операции
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }
} 