# ACID и Уровни изоляции - Демо приложение

Это Spring Boot приложение демонстрирует принципы ACID и различные уровни изоляции транзакций в базах данных.

## Принципы ACID

- **A**tomicity (Атомарность) - все операции в транзакции выполняются полностью или не выполняются вообще
- **C**onsistency (Согласованность) - транзакция переводит БД из одного согласованного состояния в другое
- **I**solation (Изоляция) - параллельные транзакции не влияют друг на друга
- **D**urability (Долговечность) - изменения сохраняются даже при сбоях

## Уровни изоляции

1. **READ UNCOMMITTED** - можно читать незакоммиченные данные (dirty read)
2. **READ COMMITTED** - читаем только закоммиченные данные, но возможны non-repeatable reads
3. **REPEATABLE READ** - гарантирует одинаковые данные при повторном чтении, но возможны phantom reads
4. **SERIALIZABLE** - полная изоляция, транзакции выполняются последовательно

## Запуск приложения

### 1. Запустите PostgreSQL
```bash
docker-compose up -d
```

### 2. Запустите приложение
```bash
./mvnw spring-boot:run
```

## Тестирование

### 1. Инициализация тестовых данных
```bash
curl -X POST http://localhost:8080/api/acid/accounts/init
```

### 2. Проверка счетов
```bash
curl http://localhost:8080/api/acid/accounts
```

## Демонстрация ACID

### Атомарность - успешный перевод
```bash
curl -X POST "http://localhost:8080/api/acid/transfer/atomic?from=ACC001&to=ACC002&amount=100"
```

### Атомарность - перевод с ошибкой (откат)
```bash
curl -X POST "http://localhost:8080/api/acid/transfer/atomic?from=ACC001&to=ACC002&amount=5000"
```

### Нарушение атомарности (без транзакции)
```bash
curl -X POST "http://localhost:8080/api/acid/transfer/non-atomic?from=ACC001&to=ACC002&amount=100&simulateError=true"
```

### Оптимистичная блокировка
```bash
curl -X POST "http://localhost:8080/api/acid/transfer/optimistic-lock?from=ACC001&to=ACC002&amount=50"
```

### Пессимистичная блокировка
```bash
curl -X POST "http://localhost:8080/api/acid/transfer/pessimistic-lock?from=ACC001&to=ACC002&amount=50"
```

## Демонстрация уровней изоляции

### READ UNCOMMITTED (Dirty Read)

В одном терминале:
```bash
curl -X POST "http://localhost:8080/api/isolation/long-update/ACC001?amount=1000"
```

В другом терминале (сразу после первого):
```bash
curl http://localhost:8080/api/isolation/read-uncommitted/ACC001
```

### READ COMMITTED (Non-Repeatable Read)

В одном терминале:
```bash
curl http://localhost:8080/api/isolation/read-committed/ACC001
```

В другом терминале (во время выполнения первого):
```bash
curl -X POST "http://localhost:8080/api/isolation/update-balance/ACC001?amount=200"
```

### REPEATABLE READ
```bash
curl http://localhost:8080/api/isolation/repeatable-read/ACC001
```

### SERIALIZABLE
```bash
curl http://localhost:8080/api/isolation/serializable
```

### Комплексная демонстрация
```bash
curl http://localhost:8080/api/isolation/demo-all/ACC001
```

## История транзакций
```bash
curl http://localhost:8080/api/acid/transactions
```

## Архитектура

- **Entity**: Account (счета), TransactionLog (логи транзакций)
- **Repository**: JPA репозитории с поддержкой блокировок
- **Service**: 
  - TransferService - демонстрация ACID
  - IsolationDemoService - демонстрация уровней изоляции
- **Controller**: REST API для тестирования

## Полезные команды

### Просмотр логов
```bash
docker-compose logs -f
```

### Остановка
```bash
docker-compose down
```

### Полная очистка (с удалением данных)
```bash
docker-compose down -v
``` 