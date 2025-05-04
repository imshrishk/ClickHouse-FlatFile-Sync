package org.example.bidirectional.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the application.
 * Provides structured error responses and centralized logging for all exceptions.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle authentication exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("Authentication error [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.UNAUTHORIZED,
                "Authentication failed",
                ex.getMessage(),
                request.getDescription(false)
        );
    }
    
    /**
     * Handle file-related exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFileNotFoundException(
            FileNotFoundException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("File not found [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.NOT_FOUND,
                "File not found",
                ex.getMessage(),
                request.getDescription(false)
        );
    }
    
    /**
     * Handle file upload size limit exceeded exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(
            MaxUploadSizeExceededException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("File upload size exceeded [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File size limit exceeded",
                "The uploaded file exceeds the maximum allowed size. Please reduce the file size and try again.",
                request.getDescription(false)
        );
    }
    
    /**
     * Handle multipart/form-data exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipartException(
            MultipartException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("Multipart request error [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.BAD_REQUEST,
                "Invalid file upload request",
                "The file upload request is invalid or malformed. Please check the file and try again.",
                request.getDescription(false)
        );
    }
    
    /**
     * Handle IO exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(
            IOException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("IO error [{}]: {}", errorId, ex.getMessage(), ex);
        
        return createErrorResponse(
                errorId,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File processing error",
                "An error occurred while processing the file. Please try again or contact support.",
                request.getDescription(false)
        );
    }
    
    /**
     * Handle missing request parameters.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParams(
            MissingServletRequestParameterException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("Missing parameter [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.BAD_REQUEST,
                "Missing required parameter",
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request.getDescription(false)
        );
    }
    
    /**
     * Handle invalid request method exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("Method not allowed [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                ex.getMessage(),
                request.getDescription(false)
        );
    }
    
    /**
     * Handle malformed JSON request body.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("Invalid request format [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.BAD_REQUEST,
                "Invalid request format",
                "The request body is invalid or malformed. Please check your input and try again.",
                request.getDescription(false)
        );
    }
    
    /**
     * Handle illegal argument exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("Invalid argument [{}]: {}", errorId, ex.getMessage());
        
        return createErrorResponse(
                errorId,
                HttpStatus.BAD_REQUEST,
                "Invalid argument",
                ex.getMessage(),
                request.getDescription(false)
        );
    }
    
    /**
     * Handle all other uncaught exceptions.
     * 
     * @param ex The exception
     * @param request The web request
     * @return Response entity with error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(
            Exception ex, WebRequest request) {
        String errorId = generateErrorId();
        logger.error("Unexpected error [{}]: {}", errorId, ex.getMessage(), ex);
        
        return createErrorResponse(
                errorId,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred. Please try again later or contact support.",
                request.getDescription(false)
        );
    }
    
    /**
     * Creates a structured error response.
     * 
     * @param errorId Unique error identifier
     * @param status HTTP status
     * @param error Error type
     * @param message Error message
     * @param path Request path
     * @return Response entity with error details
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(
            String errorId, HttpStatus status, String error, String message, String path) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now().toString());
        errorDetails.put("status", status.value());
        errorDetails.put("error", error);
        errorDetails.put("message", message);
        errorDetails.put("path", path);
        errorDetails.put("errorId", errorId);
        
        return new ResponseEntity<>(errorDetails, status);
    }
    
    /**
     * Generates a unique error ID for tracking.
     * 
     * @return Unique error ID
     */
    private String generateErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
