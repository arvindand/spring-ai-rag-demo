package com.arvindand.rag.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Translates uncaught exceptions into RFC 9457 {@link ProblemDetail} responses.
 *
 * <p>Validation, routing, and other Spring MVC errors are already rendered as ProblemDetail via
 * {@code spring.mvc.problemdetails.enabled=true}; this advice adds handlers for the cases worth a
 * tailored message.
 *
 * @author Arvind Menon
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONTENT_TOO_LARGE, "The uploaded file exceeds the maximum allowed size.");
    problem.setTitle("Content Too Large");
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception e) {
    LOG.error("Unhandled exception", e);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    problem.setTitle("Internal Server Error");
    return problem;
  }
}
