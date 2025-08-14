#!/bin/bash

# Health Check Script for Database Script Watcher
# This script performs comprehensive health checks on the application

set -e

# Configuration
APP_URL=${APP_URL:-"http://localhost:8080"}
TIMEOUT=${TIMEOUT:-10}
VERBOSE=${VERBOSE:-false}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

log_debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

# Function to check HTTP endpoint
check_endpoint() {
    local endpoint="$1"
    local expected_status="${2:-200}"
    local description="$3"
    
    log_debug "Checking endpoint: $endpoint"
    
    local response
    local status_code
    
    response=$(curl -s -w "HTTPSTATUS:%{http_code}" --max-time "$TIMEOUT" "$endpoint" 2>/dev/null || echo "HTTPSTATUS:000")
    status_code=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    
    if [[ "$status_code" == "$expected_status" ]]; then
        log_info "✓ $description - OK (HTTP $status_code)"
        return 0
    else
        log_error "✗ $description - FAILED (HTTP $status_code)"
        return 1
    fi
}

# Function to check JSON endpoint and validate content
check_json_endpoint() {
    local endpoint="$1"
    local jq_filter="$2"
    local expected_value="$3"
    local description="$4"
    
    log_debug "Checking JSON endpoint: $endpoint with filter: $jq_filter"
    
    local response
    local actual_value
    
    response=$(curl -s --max-time "$TIMEOUT" "$endpoint" 2>/dev/null)
    
    if [[ -z "$response" ]]; then
        log_error "✗ $description - No response received"
        return 1
    fi
    
    if command -v jq &> /dev/null; then
        actual_value=$(echo "$response" | jq -r "$jq_filter" 2>/dev/null)
        
        if [[ "$actual_value" == "$expected_value" ]]; then
            log_info "✓ $description - OK ($actual_value)"
            return 0
        else
            log_error "✗ $description - FAILED (expected: $expected_value, actual: $actual_value)"
            return 1
        fi
    else
        log_warn "⚠ $description - jq not available, skipping JSON validation"
        return 0
    fi
}

# Function to perform basic health checks
basic_health_checks() {
    log_info "Performing basic health checks..."
    
    local failed=0
    
    # Check main health endpoint
    if ! check_endpoint "$APP_URL/actuator/health" "200" "Application Health"; then
        ((failed++))
    fi
    
    # Check info endpoint
    if ! check_endpoint "$APP_URL/actuator/info" "200" "Application Info"; then
        ((failed++))
    fi
    
    # Check metrics endpoint
    if ! check_endpoint "$APP_URL/actuator/metrics" "200" "Metrics Endpoint"; then
        ((failed++))
    fi
    
    return $failed
}

# Function to perform detailed health checks
detailed_health_checks() {
    log_info "Performing detailed health checks..."
    
    local failed=0
    
    # Check database health
    if ! check_json_endpoint "$APP_URL/actuator/health" ".components.db.status" "UP" "Database Health"; then
        ((failed++))
    fi
    
    # Check file watcher health
    if ! check_json_endpoint "$APP_URL/actuator/health" ".components.fileWatcher.status" "UP" "File Watcher Health"; then
        ((failed++))
    fi
    
    # Check application status
    if ! check_json_endpoint "$APP_URL/actuator/health" ".status" "UP" "Overall Application Status"; then
        ((failed++))
    fi
    
    return $failed
}

# Function to check API endpoints
api_health_checks() {
    log_info "Performing API health checks..."
    
    local failed=0
    
    # Check file watcher status endpoint
    if ! check_endpoint "$APP_URL/api/filewatcher/status" "200" "File Watcher Status API"; then
        ((failed++))
    fi
    
    # Check monitoring metrics endpoint
    if ! check_endpoint "$APP_URL/api/monitoring/metrics" "200" "Monitoring Metrics API"; then
        ((failed++))
    fi
    
    # Check statistics endpoint
    if ! check_endpoint "$APP_URL/api/filewatcher/statistics" "200" "Processing Statistics API"; then
        ((failed++))
    fi
    
    return $failed
}

# Function to check Docker services
docker_health_checks() {
    log_info "Performing Docker service health checks..."
    
    local failed=0
    
    # Check if Docker is running
    if ! docker info &> /dev/null; then
        log_error "✗ Docker is not running"
        ((failed++))
        return $failed
    fi
    
    # Check application container
    if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "database-script-watcher.*Up"; then
        log_info "✓ Application container is running"
    else
        log_error "✗ Application container is not running"
        ((failed++))
    fi
    
    # Check database container (if using Docker Compose)
    if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "postgres.*Up"; then
        log_info "✓ Database container is running"
    else
        log_warn "⚠ Database container status unknown (may be external)"
    fi
    
    return $failed
}

# Function to check file system
filesystem_health_checks() {
    log_info "Performing file system health checks..."
    
    local failed=0
    local base_dir="./data"
    
    # Check if required directories exist
    local required_dirs=(
        "$base_dir/sql-scripts"
        "$base_dir/sql-scripts/completed"
        "$base_dir/sql-scripts/error"
        "$base_dir/sqlloader-logs"
        "$base_dir/sqlloader-logs/completed"
        "$base_dir/sqlloader-logs/error"
    )
    
    for dir in "${required_dirs[@]}"; do
        if [[ -d "$dir" ]]; then
            log_info "✓ Directory exists: $dir"
        else
            log_error "✗ Directory missing: $dir"
            ((failed++))
        fi
    done
    
    # Check directory permissions
    for dir in "${required_dirs[@]}"; do
        if [[ -d "$dir" && -w "$dir" ]]; then
            log_debug "Directory writable: $dir"
        elif [[ -d "$dir" ]]; then
            log_error "✗ Directory not writable: $dir"
            ((failed++))
        fi
    done
    
    return $failed
}

# Function to show system information
show_system_info() {
    if [[ "$VERBOSE" == "true" ]]; then
        log_info "System Information:"
        echo "  OS: $(uname -s)"
        echo "  Architecture: $(uname -m)"
        echo "  Kernel: $(uname -r)"
        
        if command -v docker &> /dev/null; then
            echo "  Docker Version: $(docker --version)"
        fi
        
        if command -v java &> /dev/null; then
            echo "  Java Version: $(java -version 2>&1 | head -n 1)"
        fi
        
        echo "  Current Time: $(date)"
        echo "  Uptime: $(uptime)"
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -u, --url URL       Application URL (default: http://localhost:8080)"
    echo "  -t, --timeout SEC   Request timeout in seconds (default: 10)"
    echo "  -v, --verbose       Enable verbose output"
    echo "  -b, --basic         Run only basic health checks"
    echo "  -d, --docker        Include Docker service checks"
    echo "  -f, --filesystem    Include file system checks"
    echo "  -h, --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                          # Run all health checks"
    echo "  $0 --basic                  # Run only basic checks"
    echo "  $0 --url http://prod:8080   # Check production server"
    echo "  $0 --verbose --docker       # Verbose output with Docker checks"
}

# Main function
main() {
    local basic_only=false
    local include_docker=false
    local include_filesystem=false
    local total_failed=0
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -u|--url)
                APP_URL="$2"
                shift 2
                ;;
            -t|--timeout)
                TIMEOUT="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -b|--basic)
                basic_only=true
                shift
                ;;
            -d|--docker)
                include_docker=true
                shift
                ;;
            -f|--filesystem)
                include_filesystem=true
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    log_info "Starting health checks for Database Script Watcher"
    log_info "Target URL: $APP_URL"
    log_info "Timeout: ${TIMEOUT}s"
    
    show_system_info
    
    # Run basic health checks
    basic_health_checks
    total_failed=$((total_failed + $?))
    
    # Run additional checks if not basic-only
    if [[ "$basic_only" != "true" ]]; then
        detailed_health_checks
        total_failed=$((total_failed + $?))
        
        api_health_checks
        total_failed=$((total_failed + $?))
    fi
    
    # Run Docker checks if requested
    if [[ "$include_docker" == "true" ]]; then
        docker_health_checks
        total_failed=$((total_failed + $?))
    fi
    
    # Run filesystem checks if requested
    if [[ "$include_filesystem" == "true" ]]; then
        filesystem_health_checks
        total_failed=$((total_failed + $?))
    fi
    
    # Summary
    echo ""
    if [[ $total_failed -eq 0 ]]; then
        log_info "All health checks passed! ✓"
        exit 0
    else
        log_error "$total_failed health check(s) failed! ✗"
        exit 1
    fi
}

# Run main function with all arguments
main "$@"