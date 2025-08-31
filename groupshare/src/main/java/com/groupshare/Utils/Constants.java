package com.groupshare.Utils;

import java.util.Set;

public class Constants {
    public static final Set<String> ALLOWED_TYPES = Set.of(
            // Images
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/tiff",
            "image/svg+xml", "image/x-icon", "image/heif", "image/heic",

            // Videos
            "video/mp4", "video/webm", "video/x-msvideo", "video/x-matroska",
            "video/quicktime", "video/mpeg", "video/3gpp", "video/3gpp2",
            "video/x-flv", "video/ogg", "video/x-ms-wmv",

            // Audio
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm",
            "audio/aac", "audio/flac", "audio/3gpp", "audio/3gpp2",
            "audio/mp4", "audio/x-ms-wma", "audio/amr", "audio/opus",

            // Documents
            "application/pdf", "text/plain", "text/html", "text/csv", "text/rtf",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation", "application/epub+zip",

            // Archives
            "application/zip", "application/x-7z-compressed", "application/x-rar-compressed",
            "application/x-tar", "application/gzip", "application/x-bzip2");

}
