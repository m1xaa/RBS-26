package com.tim8.oblak.CloudProject.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ProjectUploadRequest {
    private String name;
    private Map<String, String> files;
}