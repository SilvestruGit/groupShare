package com.groupshare.Entitys;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "media")
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Data
public class Media {

    @Id
    @Column(name = "id", nullable = false)
    private final UUID id;
    @Column(name = "album_id", nullable = false)
    private final UUID albumId;
    @Column(name = "file_name", nullable = false)
    private final String fileName;
    @Column(name = "file_type", nullable = false)
    private final String fileType;
    @Column(name = "file_size", nullable = false)
    private final Long fileSize;
    @Column(name = "uploaded_at", nullable = false)
    private final Instant uploadedAt;
    @Column(name = "hash", nullable = false)
    private final String hash;
}
