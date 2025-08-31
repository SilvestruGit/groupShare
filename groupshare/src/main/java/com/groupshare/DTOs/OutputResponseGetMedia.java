package com.groupshare.DTOs;

import java.util.List;
import java.util.UUID;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class OutputResponseGetMedia {

    private final UUID albumId;
    private final List<OutputResponseMedia> media;
}
