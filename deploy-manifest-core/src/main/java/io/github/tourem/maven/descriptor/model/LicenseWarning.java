package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseWarning {
    private String severity; // HIGH | MEDIUM | LOW
    private String artifact; // groupId:artifactId:version
    private String license;
    private String reason;
    private String recommendation;
}

