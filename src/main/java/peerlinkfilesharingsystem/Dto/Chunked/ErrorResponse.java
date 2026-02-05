package peerlinkfilesharingsystem.Dto.Chunked;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error response DTO
 * 
 * Used for all error scenarios
 * 
 * Example JSON (validation error):
 * {
 *   "success": false,
 *   "timestamp": "2026-01-27T01:15:30",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "errorCode": "VALIDATION_ERROR",
 *   "message": "Invalid request parameters",
 *   "details": [
 *     "File name is required",
 *     "File size must be greater than 0"
 *   ],
 *   "path": "/files/chunked/init"
 * }
 * 
 * Example JSON (session not found):
 * {
 *   "success": false,
 *   "timestamp": "2026-01-27T01:15:30",
 *   "status": 404,
 *   "error": "Not Found",
 *   "errorCode": "SESSION_NOT_FOUND",
 *   "message": "Upload session not found",
 *   "details": ["Upload ID: 7dc25d7c-... does not exist or has expired"],
 *   "path": "/files/chunked/upload",
 *   "shouldRetry": false,
 *   "suggestion": "Call /files/chunked/init to create a new session"
 * }
 * 
 * @author PeerLink Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    

    @Builder.Default
    private Boolean success = false;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private Integer status;

    private String error;

    private String errorCode;

    private String message;

    private List<String> details;

    private String path;

    private String uploadId;

    private Integer chunkNumber;

    private Boolean shouldRetry;

    private Integer retryAfterSeconds;

    private String suggestion;

    private String stackTrace;
}
