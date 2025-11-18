package com.stock.dashboard.backend.model.payload.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class AttachFileRequest {
    private List<MultipartFile> files;
    private String fileTarget;
}
