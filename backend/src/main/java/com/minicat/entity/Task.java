package com.minicat.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tasks")
public class Task {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String type; // schema_compare, schema_sync, data_compare, data_sync
    
    @Column(nullable = false)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    
    @Column
    private Integer progress; // 0-100
    
    @Column(length = 500)
    private String message;
    
    @Column(nullable = false)
    private LocalDateTime startTime;
    
    @Column
    private LocalDateTime endTime;
    
    @Column
    private String resultRef;
    
    @Column(length = 10000)
    private String logs; // JSON array of log messages
    
    @PrePersist
    protected void onCreate() {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (progress == null) {
            progress = 0;
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
