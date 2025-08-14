#!/bin/bash

# Database Script Watcher Deployment Script
# This script handles the deployment of the File Watcher service

set -e

# Configuration
APP_NAME="database-script-watcher"
VERSION=${1:-"latest"}
ENVIRONMENT=${2:-"prod"}
DOCKER_REGISTRY=${DOCKER_REGISTRY:-""}
CONFIG_DIR="./config"
DATA_DIR="./data"
LOGS_DIR="./logs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if Docker is installed and running
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        log_error "Docker is not running. Please start Docker first."
        exit 1
    fi
    
    # Check if Docker Compose is available
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose is not available. Please install Docker Compose."
        exit 1
    fi
    
    log_info "Prerequisites check passed."
}

# Function to create required directories
create_directories() {
    log_info "Creating required directories..."
    
    mkdir -p "${DATA_DIR}/sql-scripts/completed"
    mkdir -p "${DATA_DIR}/sql-scripts/error"
    mkdir -p "${DATA_DIR}/sqlloader-logs/completed"
    mkdir -p "${DATA_DIR}/sqlloader-logs/error"
    mkdir -p "${LOGS_DIR}"
    mkdir -p "./database/init"
    mkdir -p "./monitoring"
    
    log_info "Directories created successfully."
}

# Function to validate configuration
validate_configuration() {
    log_info "Validating configuration for environment: ${ENVIRONMENT}"
    
    local config_file="${CONFIG_DIR}/application-${ENVIRONMENT}.yml"
    
    if [[ ! -f "$config_file" ]]; then
        log_error "Configuration file not found: $config_file"
        exit 1
    fi
    
    # Check for required environment variables in production
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        local required_vars=("DATABASE_URL" "DATABASE_USERNAME" "DATABASE_PASSWORD")
        for var in "${required_vars[@]}"; do
            if [[ -z "${!var}" ]]; then
                log_warn "Environment variable $var is not set. Using default from config file."
            fi
        done
    fi
    
    log_info "Configuration validation completed."
}

# Function to build the application
build_application() {
    log_info "Building application..."
    
    # Build with Maven
    if [[ -f "pom.xml" ]]; then
        log_info "Building with Maven..."
        mvn clean package -DskipTests
    else
        log_error "pom.xml not found. Cannot build application."
        exit 1
    fi
    
    # Build Docker image
    log_info "Building Docker image..."
    if [[ -n "$DOCKER_REGISTRY" ]]; then
        docker build -t "${DOCKER_REGISTRY}/${APP_NAME}:${VERSION}" .
        docker tag "${DOCKER_REGISTRY}/${APP_NAME}:${VERSION}" "${DOCKER_REGISTRY}/${APP_NAME}:latest"
    else
        docker build -t "${APP_NAME}:${VERSION}" .
        docker tag "${APP_NAME}:${VERSION}" "${APP_NAME}:latest"
    fi
    
    log_info "Application built successfully."
}

# Function to run database migrations
run_migrations() {
    log_info "Running database migrations..."
    
    # Start only the database service first
    docker-compose up -d postgres
    
    # Wait for database to be ready
    log_info "Waiting for database to be ready..."
    sleep 30
    
    # Run migrations using Flyway or direct SQL execution
    if command -v flyway &> /dev/null; then
        log_info "Running Flyway migrations..."
        flyway -configFiles=database/flyway.conf migrate
    else
        log_info "Running SQL migrations directly..."
        docker-compose exec -T postgres psql -U filewatcher -d filewatcher -f /docker-entrypoint-initdb.d/01-create-database.sql
    fi
    
    log_info "Database migrations completed."
}

# Function to deploy the application
deploy_application() {
    log_info "Deploying application with environment: ${ENVIRONMENT}"
    
    # Set environment variables for Docker Compose
    export SPRING_PROFILES_ACTIVE="$ENVIRONMENT"
    export APP_VERSION="$VERSION"
    
    # Deploy using Docker Compose
    if command -v docker-compose &> /dev/null; then
        docker-compose down
        docker-compose up -d
    else
        docker compose down
        docker compose up -d
    fi
    
    log_info "Application deployed successfully."
}

# Function to verify deployment
verify_deployment() {
    log_info "Verifying deployment..."
    
    # Wait for application to start
    log_info "Waiting for application to start..."
    sleep 60
    
    # Check health endpoint
    local health_url="http://localhost:8080/actuator/health"
    local max_attempts=10
    local attempt=1
    
    while [[ $attempt -le $max_attempts ]]; do
        log_info "Health check attempt $attempt/$max_attempts..."
        
        if curl -f -s "$health_url" > /dev/null; then
            log_info "Health check passed!"
            break
        fi
        
        if [[ $attempt -eq $max_attempts ]]; then
            log_error "Health check failed after $max_attempts attempts."
            log_error "Check application logs: docker-compose logs file-watcher"
            exit 1
        fi
        
        sleep 10
        ((attempt++))
    done
    
    # Display service status
    log_info "Service status:"
    if command -v docker-compose &> /dev/null; then
        docker-compose ps
    else
        docker compose ps
    fi
    
    log_info "Deployment verification completed successfully."
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [VERSION] [ENVIRONMENT]"
    echo ""
    echo "Arguments:"
    echo "  VERSION      Application version (default: latest)"
    echo "  ENVIRONMENT  Deployment environment: dev|test|prod (default: prod)"
    echo ""
    echo "Environment Variables:"
    echo "  DOCKER_REGISTRY     Docker registry URL (optional)"
    echo "  DATABASE_URL        Database connection URL (required for prod)"
    echo "  DATABASE_USERNAME   Database username (required for prod)"
    echo "  DATABASE_PASSWORD   Database password (required for prod)"
    echo ""
    echo "Examples:"
    echo "  $0                    # Deploy latest version to prod"
    echo "  $0 1.0.0 dev          # Deploy version 1.0.0 to dev"
    echo "  $0 latest test        # Deploy latest version to test"
}

# Main deployment flow
main() {
    log_info "Starting deployment of ${APP_NAME} version ${VERSION} to ${ENVIRONMENT} environment"
    
    # Show usage if help is requested
    if [[ "$1" == "-h" || "$1" == "--help" ]]; then
        show_usage
        exit 0
    fi
    
    # Run deployment steps
    check_prerequisites
    create_directories
    validate_configuration
    build_application
    
    # Run migrations only for prod environment
    if [[ "$ENVIRONMENT" == "prod" ]]; then
        run_migrations
    fi
    
    deploy_application
    verify_deployment
    
    log_info "Deployment completed successfully!"
    log_info "Application is available at: http://localhost:8080"
    log_info "Health check: http://localhost:8080/actuator/health"
    log_info "Metrics: http://localhost:8080/actuator/metrics"
    
    if [[ "$ENVIRONMENT" == "dev" ]]; then
        log_info "H2 Console: http://localhost:8080/h2-console"
    fi
    
    log_info "Grafana Dashboard: http://localhost:3000 (admin/admin123)"
    log_info "Prometheus: http://localhost:9090"
}

# Run main function
main "$@"