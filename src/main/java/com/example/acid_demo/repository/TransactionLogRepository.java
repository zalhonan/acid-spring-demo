package com.example.acid_demo.repository;

import com.example.acid_demo.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    List<TransactionLog> findByFromAccountOrToAccountOrderByTimestampDesc(String fromAccount, String toAccount);
} 