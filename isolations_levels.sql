drop table balances;

create table balances (
id serial primary key,
user_id int,
amount int check (amount >= 0)
);

select * from balances b;

-- ^^ Создание с очищением

-- простое создание данных

insert into balances (user_id, amount) values (1, 100), (2, 100);

select * from balances b;

-- Атомарность: транзакция должна содержать обе операции
-- Консистентность - требования по amount >= 0 выполняются всегда

begin;
update balances b set amount = b.amount - 100 where id = 1;
update balances b set amount = b.amount + 100 where id = 2;
commit;

-- Для отката транзакции
-- rollback;

select * from balances b;


-- 1. READ UNCOMMITED
-- грязное чтение - данные отобразятся до окончания транзакции (выключен)
-- не может быть включен

SHOW transaction_isolation;

BEGIN;
insert into balances (user_id, amount) values (1, 100), (2, 100);
commit;

-- 2. READ COMMITED
-- Включен по умолчанию
-- если начать транзкцию, то до ее коммита другой клиента данных не увидит

begin;
insert into balances (user_id, amount) values (3, 100), (4, 100);
commit;

-- если создать два запроса SELECT, то между ними может вклиться другая транзакция 
-- и исказить результат

select * from balances b where b.amount > 0;
-- < сюда приходит еще один insert и результат искажается
select count (*) from balances b where b.amount > 0;
-- аномалия фантомного чтения: данные от одного запроса разнятся с 
-- данными похожего запроса чуть позже

-- 3. REPEATABLE READ

begin;
set transaction isolation level repeatable read;
SHOW transaction_isolation;
insert into balances (user_id, amount) values (5, 100), (6, 100);
commit;

-- считаем кол-во строк
-- запустим транзакцию чтения до begin
-- добавляем строки
-- считаем кол-во строк - оно не изменилось
-- коммитим транзакцию чтения
-- количество строк изменилось на новом чтении

-- 4. SERIALIZABLE

-- пока в процессе одна транзакция, меняющая данные, другая пройти не сможет
-- она будет ждать
-- но если вторая обнаружит, что данные изменились - то она не исполнится

insert into balances (user_id, amount) values (1, 100), (2, 100);
select * from balances;

begin;
set transaction isolation level serializable;
update balances set amount = 200 where user_id = 1;
commit;


