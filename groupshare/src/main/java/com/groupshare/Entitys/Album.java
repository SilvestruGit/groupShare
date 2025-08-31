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
@Table(name = "albums")
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Data
public class Album {

    @Id
    private final UUID id;
    @Column(name = "name", nullable = false)
    private final String name;
    @Column(name = "created_at", nullable = false)
    private final Instant createdAt;
}