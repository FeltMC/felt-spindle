package net.feltmc.spindle;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SpindlePlugin implements Plugin<Project> {
    public void apply(Project project) {
        LoomGradleExtensionAPI loomExtension = project.getExtensions().findByType(LoomGradleExtensionAPI.class);
        loomExtension.addMinecraftJarProcessor(ClassOverlayProcessor.class, "felt-spindle:overlays");
    }
}
