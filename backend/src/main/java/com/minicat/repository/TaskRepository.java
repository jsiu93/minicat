package com.minicat.repository;

import com.minicat.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    
    List<Task> findByTypeOrderByStartTimeDesc(String type);
    
    List<Task> findByStatusOrderByStartTimeDesc(String status);
    
    List<Task> findAllByOrderByStartTimeDesc();
}
