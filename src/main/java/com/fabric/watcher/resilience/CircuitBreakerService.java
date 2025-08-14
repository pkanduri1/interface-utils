package com.fabric.watcher.resilience;

import com.fabric.watcher.error.ErrorCategory;
import com.fabric.watcher.service.MetricsService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Service for managing circuit breakers to handle database connectivity issues
 * and other external system failures.
 */
@Service
public class CircuitBreakerService {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerService.class);
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MetricsService metricsService;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    @Autowired
    public CircuitBreakerService(MetricsService metricsService) {
        this.metricsService = metricsService;
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        initializeCircuitBreakers();
    }
    
    /**
     * Initialize circuit breakers for different components.
     */
    private void initializeCircuitBreakers() {
        // Database circuit breaker - more restrictive
        CircuitBreakerConfig databaseConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // Open circuit if 50% of calls fail
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30 seconds before trying again
            .slidingWindowSize(10) // Consider last 10 calls
            .minimumNumberOfCalls(5) // Need at least 5 calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open state
            .slowCallRateThreshold(80) // Consider calls slow if they take more than threshold
            .slowCallDurationThreshold(Duration.ofSeconds(5)) // Calls taking more than 5s are slow
            .build();
        
        CircuitBreaker databaseCircuitBreaker = circuitBreakerRegistry
            .circuitBreaker("database", databaseConfig);
        
        // Add event listeners for monitoring
        databaseCircuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                logger.info("Database circuit breaker state transition: {} -> {}", 
                           event.getStateTransition().getFromState(),
                           event.getStateTransition().getToState());
                metricsService.recordCircuitBreakerStateChange("database", 
                    event.getStateTransition().getToState().toString());
            })
            .onCallNotPermitted(event -> {
                logger.warn("Database call not permitted due to circuit breaker");
                metricsService.recordCircuitBreakerRejection("database");
            })
            .onError(event -> {
                logger.debug("Database circuit breaker recorded error: {}", 
                           event.getThrowable().getMessage());
            });
        
        circuitBreakers.put("database", databaseCircuitBreaker);
        
        // File system circuit breaker - less restrictive
        CircuitBreakerConfig fileSystemConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(70) // Open circuit if 70% of calls fail
            .waitDurationInOpenState(Duration.ofSeconds(15)) // Wait 15 seconds before trying again
            .slidingWindowSize(20) // Consider last 20 calls
            .minimumNumberOfCalls(10) // Need at least 10 calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(5) // Allow 5 calls in half-open state
            .build();
        
        CircuitBreaker fileSystemCircuitBreaker = circuitBreakerRegistry
            .circuitBreaker("filesystem", fileSystemConfig);
        
        fileSystemCircuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                logger.info("File system circuit breaker state transition: {} -> {}", 
                           event.getStateTransition().getFromState(),
                           event.getStateTransition().getToState());
                metricsService.recordCircuitBreakerStateChange("filesystem", 
                    event.getStateTransition().getToState().toString());
            })
            .onCallNotPermitted(event -> {
                logger.warn("File system call not permitted due to circuit breaker");
                metricsService.recordCircuitBreakerRejection("filesystem");
            });
        
        circuitBreakers.put("filesystem", fileSystemCircuitBreaker);
        
        // External system circuit breaker - moderate settings
        CircuitBreakerConfig externalSystemConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(60) // Open circuit if 60% of calls fail
            .waitDurationInOpenState(Duration.ofSeconds(20)) // Wait 20 seconds before trying again
            .slidingWindowSize(15) // Consider last 15 calls
            .minimumNumberOfCalls(8) // Need at least 8 calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(4) // Allow 4 calls in half-open state
            .build();
        
        CircuitBreaker externalSystemCircuitBreaker = circuitBreakerRegistry
            .circuitBreaker("external", externalSystemConfig);
        
        externalSystemCircuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                logger.info("External system circuit breaker state transition: {} -> {}", 
                           event.getStateTransition().getFromState(),
                           event.getStateTransition().getToState());
                metricsService.recordCircuitBreakerStateChange("external", 
                    event.getStateTransition().getToState().toString());
            });
        
        circuitBreakers.put("external", externalSystemCircuitBreaker);
    }
    
    /**
     * Execute a database operation with circuit breaker protection.
     * 
     * @param operation the operation to execute
     * @param fallback the fallback operation if circuit is open
     * @return the result of the operation or fallback
     */
    public <T> T executeDatabaseOperation(Supplier<T> operation, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = circuitBreakers.get("database");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            logger.warn("Database circuit breaker is OPEN, executing fallback");
            return fallback.get();
        }
        
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            logger.warn("Database operation failed, circuit breaker may open. Executing fallback.", e);
            return fallback.get();
        }
    }
    
    /**
     * Execute a file system operation with circuit breaker protection.
     * 
     * @param operation the operation to execute
     * @param fallback the fallback operation if circuit is open
     * @return the result of the operation or fallback
     */
    public <T> T executeFileSystemOperation(Supplier<T> operation, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = circuitBreakers.get("filesystem");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            logger.warn("File system circuit breaker is OPEN, executing fallback");
            return fallback.get();
        }
        
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            logger.warn("File system operation failed, circuit breaker may open. Executing fallback.", e);
            return fallback.get();
        }
    }
    
    /**
     * Execute an external system operation with circuit breaker protection.
     * 
     * @param operation the operation to execute
     * @param fallback the fallback operation if circuit is open
     * @return the result of the operation or fallback
     */
    public <T> T executeExternalSystemOperation(Supplier<T> operation, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = circuitBreakers.get("external");
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            logger.warn("External system circuit breaker is OPEN, executing fallback");
            return fallback.get();
        }
        
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            logger.warn("External system operation failed, circuit breaker may open. Executing fallback.", e);
            return fallback.get();
        }
    }
    
    /**
     * Check if database operations are available (circuit breaker is not open).
     * 
     * @return true if database operations are available
     */
    public boolean isDatabaseAvailable() {
        CircuitBreaker circuitBreaker = circuitBreakers.get("database");
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }
    
    /**
     * Check if file system operations are available (circuit breaker is not open).
     * 
     * @return true if file system operations are available
     */
    public boolean isFileSystemAvailable() {
        CircuitBreaker circuitBreaker = circuitBreakers.get("filesystem");
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }
    
    /**
     * Check if external system operations are available (circuit breaker is not open).
     * 
     * @return true if external system operations are available
     */
    public boolean isExternalSystemAvailable() {
        CircuitBreaker circuitBreaker = circuitBreakers.get("external");
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }
    
    /**
     * Get the appropriate circuit breaker based on error category.
     * 
     * @param category the error category
     * @return the appropriate circuit breaker name
     */
    public String getCircuitBreakerForCategory(ErrorCategory category) {
        switch (category) {
            case DATABASE:
                return "database";
            case FILE_SYSTEM:
                return "filesystem";
            case EXTERNAL_SYSTEM:
            case NETWORK:
                return "external";
            default:
                return "database"; // Default to database circuit breaker
        }
    }
    
    /**
     * Get circuit breaker state for monitoring.
     * 
     * @param name the circuit breaker name
     * @return the current state
     */
    public String getCircuitBreakerState(String name) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(name);
        return circuitBreaker != null ? circuitBreaker.getState().toString() : "NOT_FOUND";
    }
    
    /**
     * Get all circuit breaker states for monitoring.
     * 
     * @return map of circuit breaker names to their states
     */
    public Map<String, String> getAllCircuitBreakerStates() {
        Map<String, String> states = new ConcurrentHashMap<>();
        circuitBreakers.forEach((name, cb) -> states.put(name, cb.getState().toString()));
        return states;
    }
    
    /**
     * Force a circuit breaker to open (for testing or emergency situations).
     * 
     * @param name the circuit breaker name
     */
    public void forceOpen(String name) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(name);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToOpenState();
            logger.warn("Forced circuit breaker '{}' to OPEN state", name);
        }
    }
    
    /**
     * Force a circuit breaker to close (for testing or recovery situations).
     * 
     * @param name the circuit breaker name
     */
    public void forceClose(String name) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(name);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToClosedState();
            logger.info("Forced circuit breaker '{}' to CLOSED state", name);
        }
    }
}