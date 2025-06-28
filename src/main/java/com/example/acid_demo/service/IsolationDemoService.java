package com.example.acid_demo.service;

import com.example.acid_demo.entity.Account;
import com.example.acid_demo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IsolationDemoService {
    
    private final AccountRepository accountRepository;
    
    /**
     * Демонстрация DIRTY READ (грязное чтение)
     * Читаем незакоммиченные изменения другой транзакции
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public BigDecimal readUncommitted(String accountNumber) {
        log.info("READ UNCOMMITTED: Читаю баланс счёта {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal balance = account.getBalance();
        log.info("READ UNCOMMITTED: Прочитан баланс: {}", balance);
        
        // Задержка для демонстрации
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Читаем ещё раз
        account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        BigDecimal newBalance = account.getBalance();
        log.info("READ UNCOMMITTED: Повторно прочитан баланс: {}", newBalance);
        
        return newBalance;
    }
    
    /**
     * Демонстрация READ COMMITTED
     * Не видим незакоммиченные изменения, но можем увидеть разные данные при повторном чтении
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String demonstrateNonRepeatableRead(String accountNumber) {
        log.info("READ COMMITTED: Первое чтение счёта {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal firstRead = account.getBalance();
        log.info("READ COMMITTED: Первое чтение - баланс: {}", firstRead);
        
        // Задержка для изменения данных в другой транзакции
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Второе чтение
        account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        BigDecimal secondRead = account.getBalance();
        log.info("READ COMMITTED: Второе чтение - баланс: {}", secondRead);
        
        return String.format("Первое чтение: %s, Второе чтение: %s, Изменилось: %s", 
                firstRead, secondRead, !firstRead.equals(secondRead));
    }
    
    /**
     * Демонстрация REPEATABLE READ
     * Гарантирует одинаковые данные при повторном чтении, но возможны фантомные чтения
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public String demonstrateRepeatableRead(String accountNumber) {
        log.info("REPEATABLE READ: Первое чтение счёта {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal firstRead = account.getBalance();
        log.info("REPEATABLE READ: Первое чтение - баланс: {}", firstRead);
        
        // Задержка
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Второе чтение - должно быть то же значение
        account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        BigDecimal secondRead = account.getBalance();
        log.info("REPEATABLE READ: Второе чтение - баланс: {}", secondRead);
        
        // Проверка на фантомные чтения
        long countBefore = accountRepository.count();
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long countAfter = accountRepository.count();
        
        return String.format("Баланс не изменился: %s (было %s, стало %s). Количество записей: было %d, стало %d", 
                firstRead.equals(secondRead), firstRead, secondRead, countBefore, countAfter);
    }
    
    /**
     * Демонстрация SERIALIZABLE
     * Полная изоляция транзакций
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String demonstrateSerializable() {
        log.info("SERIALIZABLE: Начало транзакции");
        
        List<Account> accounts = accountRepository.findAll();
        BigDecimal totalBefore = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("SERIALIZABLE: Общая сумма на всех счетах: {}", totalBefore);
        
        // Задержка
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Повторное чтение
        accounts = accountRepository.findAll();
        BigDecimal totalAfter = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("SERIALIZABLE: Общая сумма после задержки: {}", totalAfter);
        
        return String.format("Сумма не изменилась: %s (было %s, стало %s)", 
                totalBefore.equals(totalAfter), totalBefore, totalAfter);
    }
    
    /**
     * Метод для изменения баланса (для демонстрации в другом потоке)
     */
    @Transactional
    public void updateBalance(String accountNumber, BigDecimal amount) {
        log.info("Изменяю баланс счёта {} на {}", accountNumber, amount);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Баланс изменён");
    }
    
    /**
     * Метод для долгого изменения (незакоммиченная транзакция)
     */
    @Transactional
    public void longRunningUpdate(String accountNumber, BigDecimal amount) {
        log.info("Начинаю долгое изменение баланса счёта {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Баланс изменён, но транзакция ещё не закоммичена");
        
        // Долгая операция
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("Завершаю транзакцию");
    }
} 