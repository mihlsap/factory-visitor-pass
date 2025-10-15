package com.fvps.backend.utils;

import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

/**
 * A generic wrapper for API responses that standardizes the response structure.
 *
 * <p>Contains information about the success of the operation, a timestamp,
 * a human-readable message, and the actual data payload.</p>
 *
 * <p>This class also provides static helper methods to create
 * {@link ResponseEntity} objects with either success or error states,
 * encapsulating the {@link GenericResponse} payload with appropriate HTTP status codes.</p>
 *
 * @param <T> the type of the response data payload
 */
@Data
public class GenericResponse<T> {

    /**
     * Indicates whether the request was successful.
     */
    private boolean success;

    /**
     * The timestamp of when the response was created.
     */
    private LocalDateTime timestamp;

    /**
     * A human-readable message describing the response.
     */
    private String message;

    /**
     * The data payload of the response. May be {@code null} in case of errors or empty results.
     */
    private T data;

    /**
     * Constructs a new {@code GenericResponse} with the given success status, message, and data.
     *
     * @param success indicates if the response represents a successful operation
     * @param message a descriptive message about the response
     * @param data the data payload associated with the response; may be {@code null}
     */
    public GenericResponse(boolean success, String message, T data) {
        this.success = success;
        this.timestamp = LocalDateTime.now();
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a {@link ResponseEntity} representing a successful response.
     *
     * @param message the success message to include in the response
     * @param httpStatus the HTTP status code to return
     * @param data the data payload to include in the response body
     * @param <T> the type of the data payload
     * @return a {@code ResponseEntity} containing a {@code GenericResponse} with success status
     */
    public static <T> ResponseEntity<GenericResponse<T>> success(String message, HttpStatus httpStatus, T data) {
        return ResponseEntity
                .status(httpStatus)
                .body(new GenericResponse<>(true, message, data));
    }

    /**
     * Creates a {@link ResponseEntity} representing an error response.
     *
     * @param message the error message to include in the response
     * @param httpStatus the HTTP status code to return
     * @param <T> the type of the data payload (typically {@code null} for errors)
     * @return a {@code ResponseEntity} containing a {@code GenericResponse} with failure status
     */
    public static <T> ResponseEntity<GenericResponse<T>> error(String message, HttpStatus httpStatus) {
        return ResponseEntity
                .status(httpStatus)
                .body(new GenericResponse<>(false, message, null));
    }

}
