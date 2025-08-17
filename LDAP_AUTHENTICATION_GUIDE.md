# LDAP Authentication Service Guide

This guide explains how to configure and use the LDAP Authentication Service for the Archive Search API.

## Overview

The LDAP Authentication Service provides secure user authentication against Active Directory or other LDAP servers. It supports:

- User authentication with username/password
- User details retrieval from LDAP
- Role-based authorization
- User session caching
- Comprehensive audit logging

## Configuration

### Environment Variables

Set the following environment variables to configure LDAP authentication:

```bash
# Required LDAP Configuration
export LDAP_URL="ldap://your-ad-server:389"
export LDAP_BASE_DN="dc=company,dc=com"
export LDAP_USER_SEARCH_BASE="ou=users"
export LDAP_USER_SEARCH_FILTER="(sAMAccountName={0})"

# Optional LDAP Configuration
export LDAP_USE_SSL="false"
export LDAP_BIND_DN="cn=service-account,ou=service-accounts,dc=company,dc=com"
export LDAP_BIND_PASSWORD="service-account-password"

# Enable Archive Search API
export ARCHIVE_SEARCH_ENABLED="true"
```

### Application Configuration

The LDAP configuration is defined in `application.yml`:

```yaml
archive:
  search:
    enabled: ${ARCHIVE_SEARCH_ENABLED:false}
    ldap:
      url: ${LDAP_URL:ldap://localhost:389}
      base-dn: ${LDAP_BASE_DN:dc=company,dc=com}
      user-search-base: ${LDAP_USER_SEARCH_BASE:ou=users}
      user-search-filter: ${LDAP_USER_SEARCH_FILTER:(sAMAccountName={0})}
      connection-timeout: 5000
      read-timeout: 10000
      use-ssl: ${LDAP_USE_SSL:false}
      bind-dn: ${LDAP_BIND_DN:}
      bind-password: ${LDAP_BIND_PASSWORD:}
```

## LDAP Server Requirements

### Active Directory

For Active Directory integration, ensure:

1. **Service Account** (recommended): Create a dedicated service account with read permissions
2. **User Search Base**: Point to the OU containing user accounts
3. **Search Filter**: Use `(sAMAccountName={0})` for Windows usernames
4. **Network Access**: Ensure the application can reach the AD server on port 389 (LDAP) or 636 (LDAPS)

### Generic LDAP

For other LDAP servers:

1. **Bind DN**: Use appropriate bind DN format for your LDAP server
2. **Search Filter**: Adjust the filter based on your user attribute (e.g., `(uid={0})` for OpenLDAP)
3. **Base DN**: Set to your organization's base distinguished name

## User Attributes Mapping

The service maps the following LDAP attributes to user details:

| LDAP Attribute | User Detail Field | Description |
|----------------|-------------------|-------------|
| `sAMAccountName` | `userId` | User login name |
| `displayName` | `displayName` | Full display name |
| `mail` | `email` | Email address |
| `department` | `department` | Department/division |
| `memberOf` | `groups` | Group memberships |

## Role Mapping

The service automatically maps LDAP groups to application roles:

| Group Pattern | Assigned Roles |
|---------------|----------------|
| Contains "admin" | `admin`, `file.read`, `file.upload`, `file.download` |
| Contains "developer" or "dev" | `file.read`, `file.upload`, `file.download` |
| Contains "user" | `file.read`, `file.download` |
| Default (authenticated) | `file.read` |

## API Usage

### Authentication Endpoint

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "userId": "john.doe",
  "password": "user-password"
}
```

**Response (Success):**
```json
{
  "token": "jwt-token-here",
  "userId": "john.doe",
  "expiresIn": 1800,
  "permissions": ["file.read", "file.download"]
}
```

**Response (Failure):**
```json
{
  "success": false,
  "errorMessage": "Invalid credentials"
}
```

### User Details

```http
GET /api/v1/auth/user-details?userId=john.doe
Authorization: Bearer jwt-token
```

**Response:**
```json
{
  "userId": "john.doe",
  "displayName": "John Doe",
  "email": "john.doe@company.com",
  "department": "IT",
  "groups": ["Developers", "Users"],
  "roles": ["file.read", "file.upload", "file.download"]
}
```

## Testing

### Unit Tests

Run the unit tests:

```bash
mvn test -Dtest=LdapAuthenticationServiceTest
```

### Integration Tests

To run integration tests with a real LDAP server:

1. Set up environment variables:
```bash
export LDAP_INTEGRATION_TEST="true"
export LDAP_URL="ldap://your-test-server:389"
export LDAP_BASE_DN="dc=test,dc=com"
# ... other LDAP configuration
```

2. Run the integration tests:
```bash
mvn test -Dtest=LdapAuthenticationServiceIntegrationTest
```

### Manual Testing

1. Start the application with LDAP configuration
2. Access Swagger UI at `http://localhost:8080/swagger-ui.html`
3. Navigate to the "Archive Search API with Authentication" group
4. Test the `/api/v1/auth/login` endpoint with valid credentials

## Troubleshooting

### Common Issues

1. **Connection Timeout**
   - Check network connectivity to LDAP server
   - Verify LDAP server is running and accessible
   - Check firewall rules

2. **Authentication Failed**
   - Verify user credentials
   - Check user search base and filter
   - Ensure user exists in the specified OU

3. **Bind Failed**
   - Verify service account credentials
   - Check bind DN format
   - Ensure service account has read permissions

4. **SSL/TLS Issues**
   - For LDAPS, ensure certificates are properly configured
   - Check SSL configuration and trust store

### Debug Logging

Enable debug logging for LDAP operations:

```yaml
logging:
  level:
    com.fabric.watcher.archive.service.LdapAuthenticationService: DEBUG
    org.springframework.ldap: DEBUG
```

### Health Check

The service provides health check information:

```http
GET /actuator/health
```

Look for LDAP-related health indicators in the response.

## Security Considerations

1. **Service Account**: Use a dedicated service account with minimal required permissions
2. **Password Security**: Store LDAP passwords securely using environment variables or secret management
3. **SSL/TLS**: Use LDAPS (port 636) in production environments
4. **Network Security**: Restrict network access to LDAP servers
5. **Audit Logging**: Monitor authentication attempts and failures
6. **Session Management**: Implement appropriate session timeouts

## Performance Optimization

1. **Connection Pooling**: LDAP connections are pooled by default
2. **User Caching**: User details are cached to reduce LDAP queries
3. **Timeouts**: Configure appropriate connection and read timeouts
4. **Monitoring**: Monitor LDAP response times and connection health

## Monitoring and Metrics

The service provides the following metrics:

- Authentication success/failure rates
- LDAP connection health
- User cache hit/miss ratios
- Response times for LDAP operations

Access metrics at `/actuator/metrics` or through Prometheus integration.