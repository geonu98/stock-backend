package com.stock.dashboard.backend.model.vo;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class FileMap {

    private Long id;
    private Long boardId;
    private Long fileId;
    private String fileTarget;

    @Builder
    public FileMap(Long boardId, Long fileId, String fileTarget) {
        this.boardId = boardId;
        this.fileId = fileId;
        this.fileTarget = fileTarget;
    }
}

