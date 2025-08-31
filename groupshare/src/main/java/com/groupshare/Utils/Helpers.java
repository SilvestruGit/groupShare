package com.groupshare.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.groupshare.Entitys.Media;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.Item;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Helpers {

    public static String getAlbumName(UUID albumId) {
        return "album-" + albumId;
    }

    public static void uploadFileToMinio(String bucketName, MultipartFile file, Media media, MinioClient minioClient)
            throws Exception {
        Map<String, String> metadata = Map.of(
                "filename", media.getFileName(),
                "albumid", media.getAlbumId().toString(),
                "uploadedat", media.getUploadedAt().toString(),
                "content-type", media.getFileType());

        minioClient.putObject(io.minio.PutObjectArgs.builder()
                .bucket(bucketName)
                .object(media.getId().toString()) // use UUID as object name
                .stream(file.getInputStream(), file.getSize(), -1)
                .userMetadata(metadata)
                .contentType(file.getContentType())
                .build());
    }

    public static void deleteBucketFromMinio(String bucketName, MinioClient minioClient) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (found) {
                Iterable<Result<Item>> items = minioClient.listObjects(
                        io.minio.ListObjectsArgs.builder().bucket(bucketName).build());
                for (Result<Item> item : items) {
                    String objectName = item.get().objectName();
                    minioClient.removeObject(
                            io.minio.RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
                }
                minioClient.removeBucket(RemoveBucketArgs.builder().skipValidation(true).bucket(bucketName).build());
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
                | ServerException | XmlParserException | IOException | IllegalArgumentException | InvalidKeyException
                | NoSuchAlgorithmException e) {
            System.out.println("Error deleting bucket: " + e.getMessage());
        }
    }

    public static List<Media> getMediaFromAlbumFromMinio(UUID albumId, MinioClient minioClient) {
        List<Media> mediaList = new ArrayList<>();
        String bucketName = getAlbumName(albumId);
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (found) {
                Iterable<Result<Item>> items = minioClient.listObjects(
                        io.minio.ListObjectsArgs.builder().bucket(bucketName).build());
                for (Result<Item> item : items) {
                    String objectName = item.get().objectName();
                    var stat = minioClient.statObject(
                            io.minio.StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
                    Map<String, String> userMetadata = stat.userMetadata();

                    Media media = new Media(
                            UUID.fromString(objectName),
                            UUID.fromString(userMetadata.get("albumid")),
                            userMetadata.get("filename"),
                            userMetadata.get("content-type"),
                            stat.size(),
                            Instant.parse(userMetadata.get("uploadedat")),
                            "hash");
                    mediaList.add(media);
                }
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException
                | ServerException | XmlParserException | IOException | IllegalArgumentException | InvalidKeyException
                | NoSuchAlgorithmException e) {
            // handle/log error
        }
        return mediaList;
    }

    public static InputStream downloadFileFromMinio(String bucketName, Media file, MinioClient minioClient)
            throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(file.getId().toString())
                        .build());
    }
}
