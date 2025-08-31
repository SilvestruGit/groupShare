package com.groupshare.DTOs;

import java.time.Instant;
import java.util.UUID;

import com.groupshare.Entitys.Media;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class OutputResponseMedia {

    private final UUID mediaId;
    private final String fileName;
    private final String fileType;
    private final Long fileSize;
    private final Instant uploadedAt;

    public OutputResponseMedia(Media media) {
        this.mediaId = media.getId();
        this.fileName = media.getFileName();
        this.fileType = media.getFileType();
        this.fileSize = media.getFileSize();
        this.uploadedAt = media.getUploadedAt();
    }
}
