package com.groupshare.Utils;

import java.io.InputStream;

import org.apache.tika.Tika;

public class FileValidator {
    private static final Tika tika = new Tika();

    public static boolean isAllowed(InputStream fileStream) throws Exception {
        String mimeType = tika.detect(fileStream);
        return Constants.ALLOWED_TYPES.contains(mimeType);
    }

    public static String getFileType(InputStream fileStream) throws Exception {
        String mimeType = tika.detect(fileStream);
        return mimeType;
    }
}
