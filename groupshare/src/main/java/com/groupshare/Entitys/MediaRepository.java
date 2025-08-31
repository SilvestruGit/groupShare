package com.groupshare.Entitys;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaRepository extends JpaRepository<Media, UUID> {

    List<Media> findByAlbumId(UUID albumId);

    Optional<Media> findByAlbumIdAndFileName(UUID albumId, String fileName);
}
