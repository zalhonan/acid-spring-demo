package com.example.acid_demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String fromAccount;
    
    @Column(nullable = false)
    private String toAccount;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    
    private String errorMessage;
    
    public enum TransactionStatus {
        SUCCESS, FAILED, ROLLBACK
    }
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
} 