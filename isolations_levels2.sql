-- 2. READ COMMITED

select * from balances;

-- 3. REPEATABLE READ

begin;
set transaction isolation level repeatable read;
select * from balances b;
commit;

-- 4. SERIALIZABLE

select * from balances;

begin;
set transaction isolation level serializable;
update balances set amount = 0 where user_id = 1;
commit;
