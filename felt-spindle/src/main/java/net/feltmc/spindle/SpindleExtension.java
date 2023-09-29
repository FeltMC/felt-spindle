package net.feltmc.spindle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;

public abstract class SpindleExtension {
	
	@Optional
	public abstract RegularFileProperty getAccessTransformerPath();
	
	@Optional
	public abstract Property<Boolean> getOverwriteAccessWidener();
	
//	@Optional
//	public abstract Property<Boolean> getAutoConvertATToAW();
//	
//	@Optional
//	public abstract Property<Boolean> getApplyATInDev();
	
}
