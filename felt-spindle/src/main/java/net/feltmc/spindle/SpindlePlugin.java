package net.feltmc.spindle;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.feltmc.spindle.processors.ClassOverlayProcessor;
import net.feltmc.spindle.task.GenerateAccessWidenerFromTransformerTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SpindlePlugin implements Plugin<Project> {
    
    public void apply(Project project) {
        final LoomGradleExtensionAPI loom = project.getExtensions().findByType(LoomGradleExtensionAPI.class);
        if (loom == null)
            throw new AssertionError("Fabric Loom not found!");
        
        loom.addMinecraftJarProcessor(ClassOverlayProcessor.class, "felt-spindle:overlays");
        
        var config = project.getExtensions().create("spindle", SpindleExtension.class);
        
        project.getTasks().register("generateAccessWidenerFromTransformer", GenerateAccessWidenerFromTransformerTask.class, task -> {
            task.getAccessWidenerPath().set(loom.getAccessWidenerPath());
            task.getAccessTransformerPath().set(config.getAccessTransformerPath());
            task.getProjectMappingsFile().set(loom::getMappingsFile);
            //noinspection UnstableApiUsage
            task.getMinecraftVersion().set(loom.getIntermediateMappingsProvider().getMinecraftVersion());
            //noinspection UnstableApiUsage
            task.getMinecraftVersionMeta().set(((LoomGradleExtension) loom).getMinecraftProvider().getVersionInfo());
            task.getOverwriteAccessWidener().set(config.getOverwriteAccessWidener());
        });
    }
    
}
