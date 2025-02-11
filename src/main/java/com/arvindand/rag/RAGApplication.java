package com.arvindand.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the recommendation system service. This class serves as the entry
 * point for the Spring Boot application.
 *
 * @author Arvind Menon
 */
@SpringBootApplication
class RAGApplication {

  public static void main(String[] args) {
    SpringApplication.run(RAGApplication.class, args);
  }
}
