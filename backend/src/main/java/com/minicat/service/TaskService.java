package com.minicat.service;

import com.minicat.entity.Task;
import com.minicat.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {
    
    private final TaskRepository taskRepository;
    
    public List<Task> getAllTasks() {
        return taskRepository.findAllByOrderByStartTimeDesc();
    }
    
    public Task getTaskById(String id) {
        return taskRepository.findById(id).orElse(null);
    }
    
    public List<Task> getTasksByType(String type) {
        return taskRepository.findByTypeOrderByStartTimeDesc(type);
    }
    
    public List<Task> getTasksByStatus(String status) {
        return taskRepository.findByStatusOrderByStartTimeDesc(status);
    }
    
    public Task createTask(String type) {
        String taskId = generateTaskId(type);
        Task task = Task.builder()
                .id(taskId)
                .type(type)
                .status("PENDING")
                .progress(0)
                .message("Task created")
                .startTime(LocalDateTime.now())
                .logs("[]")
                .build();
        
        return taskRepository.save(task);
    }
    
    public Task updateTaskProgress(String id, int progress, String message) {
        Task task = getTaskById(id);
        if (task != null) {
            task.setProgress(progress);
            task.setMessage(message);
            if (progress >= 100) {
                task.setStatus("COMPLETED");
                task.setEndTime(LocalDateTime.now());
            } else {
                task.setStatus("RUNNING");
            }
            return taskRepository.save(task);
        }
        return null;
    }
    
    public Task failTask(String id, String errorMessage) {
        Task task = getTaskById(id);
        if (task != null) {
            task.setStatus("FAILED");
            task.setMessage(errorMessage);
            task.setEndTime(LocalDateTime.now());
            return taskRepository.save(task);
        }
        return null;
    }
    
    private String generateTaskId(String type) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("task_%s_%s", type, timestamp);
    }
}
