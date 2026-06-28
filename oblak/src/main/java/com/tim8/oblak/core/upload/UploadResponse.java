package com.tim8.oblak.core.upload;

import java.util.UUID;

public record UploadResponse(UUID projectId, String executeUrl) {
}