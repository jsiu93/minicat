package com.minicat.controller;

import com.minicat.entity.Task;
import com.minicat.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Tag(name = "Task", description = "Task management API")
public class TaskController {
    
    private final TaskService taskService;
    
    @GetMapping
    @Operation(summary = "Get all tasks")
    public ResponseEntity<List<Task>> getAllTasks(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        
        if (type != null) {
            return ResponseEntity.ok(taskService.getTasksByType(type));
        }
        if (status != null) {
            return ResponseEntity.ok(taskService.getTasksByStatus(status));
        }
        return ResponseEntity.ok(taskService.getAllTasks());
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID")
    public ResponseEntity<Task> getTaskById(@PathVariable String id) {
        Task task = taskService.getTaskById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }
    
    @PostMapping
    @Operation(summary = "Create new task")
    public ResponseEntity<Task> createTask(@RequestParam String type) {
        Task task = taskService.createTask(type);
        return ResponseEntity.ok(task);
    }
}
