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
public class LicenseDetail {
    private String groupId;
    private String artifactId;
    private String version;
    private String scope;
    private String license;
    private String licenseUrl;
    private Boolean multiLicense;
    private Integer depth; // 1 for direct for now
}

