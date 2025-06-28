package com.example.acid_demo.service;

import com.example.acid_demo.entity.Account;
import com.example.acid_demo.repository.AccountRepository;
import com.example.acid_demo.util.JsonLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IsolationDemoService {
    
    private final AccountRepository accountRepository;
    private final JsonLogger jsonLogger;
    
    /**
     * Демонстрация DIRTY READ (грязное чтение)
     * Читаем незакоммиченные изменения другой транзакции
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public BigDecimal readUncommitted(String accountNumber) {
        jsonLogger.logOperation("ДЕМОНСТРАЦИЯ READ UNCOMMITTED", Map.of(
            "уровень_изоляции", "READ_UNCOMMITTED",
            "счёт", accountNumber,
            "описание", "Читаем незакоммиченные изменения других транзакций"
        ));
        
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal balance = account.getBalance();
        jsonLogger.logInfo("Первое чтение баланса", Map.of(
            "счёт", accountNumber,
            "баланс", balance,
            "время", System.currentTimeMillis()
        ));
        
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
        
        jsonLogger.logInfo("Второе чтение баланса", Map.of(
            "счёт", accountNumber,
            "баланс", newBalance,
            "изменился", !balance.equals(newBalance),
            "время", System.currentTimeMillis()
        ));
        
        return newBalance;
    }
    
    /**
     * Демонстрация READ COMMITTED
     * Не видим незакоммиченные изменения, но можем увидеть разные данные при повторном чтении
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String demonstrateNonRepeatableRead(String accountNumber) {
        jsonLogger.logOperation("ДЕМОНСТРАЦИЯ READ COMMITTED", Map.of(
            "уровень_изоляции", "READ_COMMITTED",
            "счёт", accountNumber,
            "описание", "Не видим незакоммиченные изменения, но можем увидеть разные данные при повторном чтении"
        ));
        
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal firstRead = account.getBalance();
        jsonLogger.logInfo("Первое чтение", Map.of(
            "счёт", accountNumber,
            "баланс", firstRead,
            "время", System.currentTimeMillis()
        ));
        
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
        
        boolean changed = !firstRead.equals(secondRead);
        jsonLogger.logInfo("Второе чтение", Map.of(
            "счёт", accountNumber,
            "баланс_первое_чтение", firstRead,
            "баланс_второе_чтение", secondRead,
            "изменился", changed,
            "время", System.currentTimeMillis()
        ));
        
        return String.format("Первое чтение: %s, Второе чтение: %s, Изменилось: %s", 
                firstRead, secondRead, changed);
    }
    
    /**
     * Демонстрация REPEATABLE READ
     * Гарантирует одинаковые данные при повторном чтении, но возможны фантомные чтения
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public String demonstrateRepeatableRead(String accountNumber) {
        jsonLogger.logOperation("ДЕМОНСТРАЦИЯ REPEATABLE READ", Map.of(
            "уровень_изоляции", "REPEATABLE_READ",
            "счёт", accountNumber,
            "описание", "Гарантирует одинаковые данные при повторном чтении"
        ));
        
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal firstRead = account.getBalance();
        long countBefore = accountRepository.count();
        
        jsonLogger.logInfo("Первое чтение", Map.of(
            "счёт", accountNumber,
            "баланс", firstRead,
            "количество_записей", countBefore,
            "время", System.currentTimeMillis()
        ));
        
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
        
        // Проверка на фантомные чтения
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long countAfter = accountRepository.count();
        
        jsonLogger.logInfo("Второе чтение", Map.of(
            "счёт", accountNumber,
            "баланс_первое_чтение", firstRead,
            "баланс_второе_чтение", secondRead,
            "баланс_не_изменился", firstRead.equals(secondRead),
            "количество_записей_до", countBefore,
            "количество_записей_после", countAfter,
            "время", System.currentTimeMillis()
        ));
        
        return String.format("Баланс не изменился: %s (было %s, стало %s). Количество записей: было %d, стало %d", 
                firstRead.equals(secondRead), firstRead, secondRead, countBefore, countAfter);
    }
    
    /**
     * Демонстрация SERIALIZABLE
     * Полная изоляция транзакций
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String demonstrateSerializable() {
        jsonLogger.logOperation("ДЕМОНСТРАЦИЯ SERIALIZABLE", Map.of(
            "уровень_изоляции", "SERIALIZABLE",
            "описание", "Полная изоляция транзакций"
        ));
        
        List<Account> accounts = accountRepository.findAll();
        BigDecimal totalBefore = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        jsonLogger.logInfo("Начальное состояние", Map.of(
            "количество_счетов", accounts.size(),
            "общая_сумма", totalBefore,
            "счета", accounts.stream().map(acc -> Map.of(
                "номер", acc.getAccountNumber(),
                "баланс", acc.getBalance()
            )).toList()
        ));
        
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
        
        jsonLogger.logInfo("Конечное состояние", Map.of(
            "количество_счетов", accounts.size(),
            "общая_сумма", totalAfter,
            "сумма_не_изменилась", totalBefore.equals(totalAfter),
            "счета", accounts.stream().map(acc -> Map.of(
                "номер", acc.getAccountNumber(),
                "баланс", acc.getBalance()
            )).toList()
        ));
        
        return String.format("Сумма не изменилась: %s (было %s, стало %s)", 
                totalBefore.equals(totalAfter), totalBefore, totalAfter);
    }
    
    /**
     * Метод для изменения баланса (для демонстрации в другом потоке)
     */
    @Transactional
    public void updateBalance(String accountNumber, BigDecimal amount) {
        jsonLogger.logOperation("ИЗМЕНЕНИЕ БАЛАНСА", Map.of(
            "счёт", accountNumber,
            "сумма_изменения", amount,
            "операция", "UPDATE"
        ));
        
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal oldBalance = account.getBalance();
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        
        jsonLogger.logInfo("Баланс изменён", Map.of(
            "счёт", accountNumber,
            "старый_баланс", oldBalance,
            "новый_баланс", account.getBalance(),
            "изменение", amount
        ));
    }
    
    /**
     * Метод для долгого изменения (незакоммиченная транзакция)
     */
    @Transactional
    public void longRunningUpdate(String accountNumber, BigDecimal amount) {
        jsonLogger.logOperation("ДОЛГАЯ ТРАНЗАКЦИЯ", Map.of(
            "счёт", accountNumber,
            "сумма_изменения", amount,
            "длительность", "5 секунд"
        ));
        
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        BigDecimal oldBalance = account.getBalance();
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        
        jsonLogger.logInfo("Баланс изменён, но транзакция НЕ закоммичена", Map.of(
            "счёт", accountNumber,
            "старый_баланс", oldBalance,
            "новый_баланс", account.getBalance(),
            "статус", "UNCOMMITTED"
        ));
        
        // Долгая операция
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        jsonLogger.logInfo("Транзакция завершается", Map.of(
            "счёт", accountNumber,
            "финальный_баланс", account.getBalance(),
            "статус", "COMMITTING"
        ));
    }
} 