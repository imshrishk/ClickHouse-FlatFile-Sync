package org.example.bidirectional.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        logger.info("Test ping endpoint called");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Server is responding");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(@RequestBody(required = false) Map<String, Object> body) {
        logger.info("Test echo endpoint called with body: {}", body);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("received", body != null ? body : "No body sent");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> testUpload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "test", required = false) String test) {
        
        logger.info("Test upload endpoint called. File: {}, Test param: {}", 
                file != null ? file.getOriginalFilename() : "none", 
                test != null ? test : "none");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        
        if (file != null) {
            response.put("filename", file.getOriginalFilename());
            response.put("size", file.getSize());
            response.put("contentType", file.getContentType());
        }
        
        if (test != null) {
            response.put("test", test);
        }
        
        return ResponseEntity.ok(response);
    }
} 