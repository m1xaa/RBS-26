package com.tim8.oblak.core.upload;

import com.tim8.oblak.core.validation.ZipValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@RequiredArgsConstructor
public class UploadService {

    private final ZipValidation zipValidation;

    public void upload(MultipartFile file) {
        zipValidation.validateZipFile(file);
    }
}
