package com.stock.dashboard.backend.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class UploadFile {

    private Long id;

    private String fileTarget;

    private String fileName;

    private String saveFileName;

    @JsonIgnore
    private String filePath;

    @JsonIgnore
    private String fileDir;

    private String contentType;

    private long fileSize;

    @JsonIgnore
    private String username;

    @JsonIgnore
    private LocalDateTime createdAt;

    public UploadFile(String fileName, String saveFileName, String filePath, String contentType, long fileSize, String fileDir, String fileTarget, String username) {
        this.fileName = fileName;
        this.saveFileName = saveFileName;
        this.filePath = filePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileDir = fileDir;
        this.fileTarget = fileTarget;
        this.username = username;
    }

    @Builder
    public UploadFile(String fileTarget, String fileName, String saveFileName, String filePath, String fileDir, String contentType, long fileSize, LocalDateTime createdAt, String username) {
        this.fileTarget = fileTarget;
        this.fileName = fileName;
        this.saveFileName = saveFileName;
        this.filePath = filePath;
        this.fileDir = fileDir;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
        this.username = username;
    }
}

