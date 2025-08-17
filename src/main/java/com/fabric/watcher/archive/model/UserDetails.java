package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * User details model containing information about an authenticated user.
 * 
 * <p>This class represents detailed information about a user retrieved
 * from LDAP during authentication.</p>
 * 
 * @since 1.0
 */
@Schema(description = "User details containing information about an authenticated user")
public class UserDetails {

    /**
     * The user ID.
     */
    @Schema(description = "User ID", example = "john.doe")
    @JsonProperty("userId")
    private String userId;

    /**
     * The user's display name.
     */
    @Schema(description = "User's display name", example = "John Doe")
    @JsonProperty("displayName")
    private String displayName;

    /**
     * The user's email address.
     */
    @Schema(description = "User's email address", example = "john.doe@company.com")
    @JsonProperty("email")
    private String email;

    /**
     * The user's department.
     */
    @Schema(description = "User's department", example = "IT")
    @JsonProperty("department")
    private String department;

    /**
     * List of groups the user belongs to.
     */
    @Schema(description = "List of groups the user belongs to", example = "[\"Administrators\", \"Developers\"]")
    @JsonProperty("groups")
    private List<String> groups;

    /**
     * List of roles assigned to the user.
     */
    @Schema(description = "List of roles assigned to the user", example = "[\"file.read\", \"file.upload\"]")
    @JsonProperty("roles")
    private List<String> roles;

    /**
     * Default constructor.
     */
    public UserDetails() {
    }

    /**
     * Constructor with parameters.
     *
     * @param userId      the user ID
     * @param displayName the display name
     * @param email       the email address
     * @param department  the department
     * @param groups      the list of groups
     * @param roles       the list of roles
     */
    public UserDetails(String userId, String displayName, String email, String department, 
                      List<String> groups, List<String> roles) {
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
        this.department = department;
        this.groups = groups;
        this.roles = roles;
    }

    /**
     * Gets the user ID.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID.
     *
     * @param userId the user ID to set
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Gets the display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name.
     *
     * @param displayName the display name to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the email address.
     *
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email address.
     *
     * @param email the email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Gets the department.
     *
     * @return the department
     */
    public String getDepartment() {
        return department;
    }

    /**
     * Sets the department.
     *
     * @param department the department to set
     */
    public void setDepartment(String department) {
        this.department = department;
    }

    /**
     * Gets the list of groups.
     *
     * @return the list of groups
     */
    public List<String> getGroups() {
        return groups;
    }

    /**
     * Sets the list of groups.
     *
     * @param groups the list of groups to set
     */
    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    /**
     * Gets the list of roles.
     *
     * @return the list of roles
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Sets the list of roles.
     *
     * @param roles the list of roles to set
     */
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    @Override
    public String toString() {
        return "UserDetails{" +
                "userId='" + userId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", email='" + email + '\'' +
                ", department='" + department + '\'' +
                ", groups=" + groups +
                ", roles=" + roles +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDetails that = (UserDetails) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(displayName, that.displayName) &&
                Objects.equals(email, that.email) &&
                Objects.equals(department, that.department) &&
                Objects.equals(groups, that.groups) &&
                Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, displayName, email, department, groups, roles);
    }
}