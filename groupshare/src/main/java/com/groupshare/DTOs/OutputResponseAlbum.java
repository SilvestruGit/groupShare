package com.groupshare.DTOs;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class OutputResponseAlbum {

    private final UUID id;
    private final Instant createdAt;
}
