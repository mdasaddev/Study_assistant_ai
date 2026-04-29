package com.study.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyResponse {
    private String result;
    private String type;
    private long durationMs;
    private boolean success;
    private String error;

    public static StudyResponse error(String msg) {
        return StudyResponse.builder()
                .success(false)
                .error(msg)
                .result("Error: " + msg)
                .build();
    }
}
