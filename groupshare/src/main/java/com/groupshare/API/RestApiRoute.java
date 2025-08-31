package com.groupshare.API;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.groupshare.DTOs.InputRequestAlbum;
import com.groupshare.DTOs.OutputResponseAlbum;
import com.groupshare.DTOs.OutputResponseGetMedia;
import com.groupshare.DTOs.OutputResponseMedia;
import com.groupshare.Entitys.Album;
import com.groupshare.Entitys.AlbumRepository;
import com.groupshare.Entitys.Media;
import com.groupshare.Entitys.MediaRepository;
import com.groupshare.Utils.Constants;
import com.groupshare.Utils.FileValidator;
import com.groupshare.Utils.Helpers;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api")
public class RestApiRoute {

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.accessKey}")
    private String minioAccessKey;

    @Value("${minio.secretKey}")
    private String minioSecretKey;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private MediaRepository mediaRepository;

    private MinioClient minioClient;

    @PostConstruct
    public void initMinio() {
        this.minioClient = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    @PostMapping("/albums")
    public ResponseEntity<OutputResponseAlbum> createAlbum(@RequestBody InputRequestAlbum entity) throws Exception {

        if (albumRepository.findByName(entity.getName()) != null) {
            return ResponseEntity.status(409).build();
        }

        if (entity.getName() == null || entity.getName().isBlank()) {
            return ResponseEntity.status(400).build();
        }

        Album newAlbum = new Album(UUID.randomUUID(), entity.getName(), Instant.now());
        albumRepository.save(newAlbum);
        String albumName = "album-" + newAlbum.getId();

        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(albumName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(albumName).build());
        } else {
            System.out.println("Bucket 'test' already exists.");
        }

        OutputResponseAlbum response = new OutputResponseAlbum(newAlbum.getId(), newAlbum.getCreatedAt());

        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("albums/{albumId}/upload")
    public ResponseEntity<OutputResponseMedia> postMethodName(@PathVariable UUID albumId,
            @RequestParam("file") MultipartFile file) throws Exception {

        Album album = albumRepository.findById(albumId).orElse(null);
        if (album == null) {
            return ResponseEntity.status(400).build();
        }

        String albumName = Helpers.getAlbumName(albumId);
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(albumName).build());
        if (!found) {
            return ResponseEntity.status(400).build();
        }

        if (mediaRepository.findByAlbumIdAndFileName(albumId, file.getOriginalFilename()).isPresent()) {
            return ResponseEntity.status(409).build(); // Conflict
        }

        // Validate file type using FileValidator and file.getContentType()
        if (!FileValidator.isAllowed(file.getInputStream())
                || !Constants.ALLOWED_TYPES.contains(file.getContentType())) {
            return ResponseEntity.status(415).build(); // Unsupported Media Type
        }

        Media entity = new Media(UUID.randomUUID(), albumId, file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(), Instant.now(), "hash");
        mediaRepository.save(entity);

        Helpers.uploadFileToMinio(albumName, file, entity, minioClient);

        OutputResponseMedia response = new OutputResponseMedia(entity.getId(), entity.getFileName(),
                entity.getFileType(), entity.getFileSize(), entity.getUploadedAt());

        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("albums/{albumId}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable UUID albumId) {

        Helpers.deleteBucketFromMinio(Helpers.getAlbumName(albumId), minioClient);

        Album album = albumRepository.findById(albumId).orElse(null);
        if (album == null) {
            return ResponseEntity.status(404).build();
        }

        mediaRepository.deleteAll(mediaRepository.findByAlbumId(albumId));

        albumRepository.delete(album);
        return ResponseEntity.status(204).build();
    }

    @GetMapping("albums/{albumId}/media")
    public ResponseEntity<OutputResponseGetMedia> getMedia(@PathVariable UUID albumId) {

        List<Media> mediaList = Helpers.getMediaFromAlbumFromMinio(albumId, minioClient);
        if (mediaList == null || mediaList.isEmpty()) {
            return ResponseEntity.status(200).body(new OutputResponseGetMedia(albumId, new ArrayList<>()));
        }
        List<OutputResponseMedia> responseList = new ArrayList<>();
        for (Media media : mediaList) {
            responseList.add(new OutputResponseMedia(media));
        }

        return ResponseEntity.status(200).body(new OutputResponseGetMedia(albumId, responseList));
    }

    @DeleteMapping("media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(@PathVariable UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null) {
            return ResponseEntity.status(404).build();
        }

        String bucketName = Helpers.getAlbumName(media.getAlbumId());
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (found) {
                Iterable<io.minio.Result<io.minio.messages.Item>> items = minioClient.listObjects(
                        io.minio.ListObjectsArgs.builder().bucket(bucketName).build());
                for (io.minio.Result<io.minio.messages.Item> item : items) {
                    String objectName = item.get().objectName();
                    if (objectName.equals(media.getFileName())) {
                        minioClient.removeObject(
                                io.minio.RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
                        break;
                    }
                }
            } else {
                return ResponseEntity.status(404).build();
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
                | ServerException | XmlParserException | IOException | IllegalArgumentException | InvalidKeyException
                | NoSuchAlgorithmException e) {
            return ResponseEntity.status(500).build();
        }

        mediaRepository.delete(media);
        return ResponseEntity.status(204).build();
    }

    @GetMapping("media/{mediaId}/download")
    public ResponseEntity<byte[]> downloadMediaFile(@PathVariable UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null) {
            return ResponseEntity.status(404).build();
        }
        UUID albumId = media.getAlbumId();
        try {
            byte[] fileBytes;
            try (InputStream downloadStream = Helpers.downloadFileFromMinio(Helpers.getAlbumName(albumId), media,
                    minioClient)) {
                fileBytes = downloadStream.readAllBytes();
            }
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + media.getFileName() + "\"")
                    .header("Content-Type", media.getFileType())
                    .body(fileBytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

}
