package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Container for plugins information in the build.
 * author: tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PluginInfo {
    private PluginSummary summary;
    private List<PluginDetail> list;
    private List<PluginManagementEntry> management;
    private List<PluginDetail> outdated; // optional detailed list of outdated plugins
}

