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
public class LicenseCompliance {
    private Boolean hasIncompatibleLicenses;
    private Integer incompatibleCount;
    private Integer unknownCount;
    private Boolean commerciallyViable;
    private Boolean requiresAttribution;
}

