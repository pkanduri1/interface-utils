package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Response model for file upload operations.
 * 
 * <p>This class represents the response after a file upload operation,
 * containing success status, file information, and any relevant messages.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Response for file upload operations")
public class UploadResponse {

    /**
     * Whether the upload was successful.
     */
    @Schema(description = "Whether the upload was successful", example = "true")
    @JsonProperty("success")
    private boolean success;

    /**
     * The name of the uploaded file.
     */
    @Schema(description = "Name of the uploaded file", example = "config.properties")
    @JsonProperty("fileName")
    private String fileName;

    /**
     * The target path where the file was uploaded.
     */
    @Schema(description = "Target path where the file was uploaded", example = "/opt/uploads/config.properties")
    @JsonProperty("targetPath")
    private String targetPath;

    /**
     * The size of the uploaded file in bytes.
     */
    @Schema(description = "Size of the uploaded file in bytes", example = "1024")
    @JsonProperty("fileSize")
    private long fileSize;

    /**
     * Response message providing additional information.
     */
    @Schema(description = "Response message providing additional information", 
            example = "File uploaded successfully")
    @JsonProperty("message")
    private String message;

    /**
     * Timestamp when the upload was completed.
     */
    @Schema(description = "Timestamp when the upload was completed")
    @JsonProperty("uploadedAt")
    private LocalDateTime uploadedAt;

    /**
     * The user who performed the upload.
     */
    @Schema(description = "User who performed the upload", example = "john.doe")
    @JsonProperty("uploadedBy")
    private String uploadedBy;

    /**
     * Default constructor.
     */
    public UploadResponse() {
    }

    /**
     * Constructor for successful upload.
     *
     * @param fileName   the uploaded file name
     * @param targetPath the target path
     * @param fileSize   the file size
     * @param uploadedBy the user who uploaded the file
     */
    public UploadResponse(String fileName, String targetPath, long fileSize, String uploadedBy) {
        this.success = true;
        this.fileName = fileName;
        this.targetPath = targetPath;
        this.fileSize = fileSize;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
        this.message = "File uploaded successfully";
    }

    /**
     * Constructor for failed upload.
     *
     * @param fileName the file name that failed to upload
     * @param message  the error message
     */
    public UploadResponse(String fileName, String message) {
        this.success = false;
        this.fileName = fileName;
        this.message = message;
        this.uploadedAt = LocalDateTime.now();
    }

    /**
     * Constructor with all parameters.
     *
     * @param success     whether the upload was successful
     * @param fileName    the file name
     * @param targetPath  the target path
     * @param fileSize    the file size
     * @param message     the response message
     * @param uploadedAt  the upload timestamp
     * @param uploadedBy  the user who uploaded the file
     */
    public UploadResponse(boolean success, String fileName, String targetPath, long fileSize, 
                         String message, LocalDateTime uploadedAt, String uploadedBy) {
        this.success = success;
        this.fileName = fileName;
        this.targetPath = targetPath;
        this.fileSize = fileSize;
        this.message = message;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
    }

    /**
     * Gets whether the upload was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets whether the upload was successful.
     *
     * @param success true if successful, false otherwise
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Gets the file name.
     *
     * @return the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the file name.
     *
     * @param fileName the file name to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the target path.
     *
     * @return the target path
     */
    public String getTargetPath() {
        return targetPath;
    }

    /**
     * Sets the target path.
     *
     * @param targetPath the target path to set
     */
    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    /**
     * Gets the file size.
     *
     * @return the file size in bytes
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Sets the file size.
     *
     * @param fileSize the file size in bytes to set
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Gets the response message.
     *
     * @return the response message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the response message.
     *
     * @param message the response message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the upload timestamp.
     *
     * @return the upload timestamp
     */
    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    /**
     * Sets the upload timestamp.
     *
     * @param uploadedAt the upload timestamp to set
     */
    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    /**
     * Gets the user who uploaded the file.
     *
     * @return the user who uploaded the file
     */
    public String getUploadedBy() {
        return uploadedBy;
    }

    /**
     * Sets the user who uploaded the file.
     *
     * @param uploadedBy the user who uploaded the file to set
     */
    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    @Override
    public String toString() {
        return "UploadResponse{" +
                "success=" + success +
                ", fileName='" + fileName + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", fileSize=" + fileSize +
                ", message='" + message + '\'' +
                ", uploadedAt=" + uploadedAt +
                ", uploadedBy='" + uploadedBy + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadResponse that = (UploadResponse) o;
        return success == that.success &&
                fileSize == that.fileSize &&
                Objects.equals(fileName, that.fileName) &&
                Objects.equals(targetPath, that.targetPath) &&
                Objects.equals(message, that.message) &&
                Objects.equals(uploadedAt, that.uploadedAt) &&
                Objects.equals(uploadedBy, that.uploadedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, fileName, targetPath, fileSize, message, uploadedAt, uploadedBy);
    }
}