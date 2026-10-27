package com.paymatch.export;

import org.springframework.http.MediaType;

public enum ExportFormat {
    CSV("csv", MediaType.parseMediaType("text/csv; charset=UTF-8")),
    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final String extension;
    private final MediaType mediaType;

    ExportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String getExtension() {
        return extension;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
