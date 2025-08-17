package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * Request model for file upload operations.
 * 
 * <p>This class represents a file upload request containing the file to upload,
 * target path, and upload options.</p>
 * 
 * @since 1.0
 */
@Schema(description = "File upload request containing file and target path information")
public class UploadRequest {

    /**
     * The file to upload.
     */
    @Schema(description = "The file to upload", required = true)
    @NotNull(message = "File cannot be null")
    @JsonProperty("file")
    private MultipartFile file;

    /**
     * The target path where the file should be uploaded.
     */
    @Schema(description = "Target path where the file should be uploaded", 
            example = "/opt/uploads/myfile.txt", required = true)
    @NotBlank(message = "Target path cannot be blank")
    @Size(min = 1, max = 500, message = "Target path must be between 1 and 500 characters")
    @JsonProperty("targetPath")
    private String targetPath;

    /**
     * Whether to overwrite existing files.
     */
    @Schema(description = "Whether to overwrite existing files", example = "false")
    @JsonProperty("overwrite")
    private boolean overwrite = false;

    /**
     * Optional description for the upload.
     */
    @Schema(description = "Optional description for the upload", example = "Configuration file update")
    @Size(max = 255, message = "Description must not exceed 255 characters")
    @JsonProperty("description")
    private String description;

    /**
     * Default constructor.
     */
    public UploadRequest() {
    }

    /**
     * Constructor with required parameters.
     *
     * @param file       the file to upload
     * @param targetPath the target path
     */
    public UploadRequest(MultipartFile file, String targetPath) {
        this.file = file;
        this.targetPath = targetPath;
        this.overwrite = false;
    }

    /**
     * Constructor with all parameters.
     *
     * @param file        the file to upload
     * @param targetPath  the target path
     * @param overwrite   whether to overwrite existing files
     * @param description optional description
     */
    public UploadRequest(MultipartFile file, String targetPath, boolean overwrite, String description) {
        this.file = file;
        this.targetPath = targetPath;
        this.overwrite = overwrite;
        this.description = description;
    }

    /**
     * Gets the file to upload.
     *
     * @return the file to upload
     */
    public MultipartFile getFile() {
        return file;
    }

    /**
     * Sets the file to upload.
     *
     * @param file the file to upload
     */
    public void setFile(MultipartFile file) {
        this.file = file;
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
     * Gets whether to overwrite existing files.
     *
     * @return true if overwrite is enabled, false otherwise
     */
    public boolean isOverwrite() {
        return overwrite;
    }

    /**
     * Sets whether to overwrite existing files.
     *
     * @param overwrite true to enable overwrite, false otherwise
     */
    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "UploadRequest{" +
                "file=" + (file != null ? file.getOriginalFilename() : "null") +
                ", targetPath='" + targetPath + '\'' +
                ", overwrite=" + overwrite +
                ", description='" + description + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadRequest that = (UploadRequest) o;
        return overwrite == that.overwrite &&
                Objects.equals(file, that.file) &&
                Objects.equals(targetPath, that.targetPath) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, targetPath, overwrite, description);
    }
}