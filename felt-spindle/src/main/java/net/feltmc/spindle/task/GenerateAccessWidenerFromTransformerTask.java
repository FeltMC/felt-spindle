package net.feltmc.spindle.task;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.mappingio.tree.MappingTree;
import net.feltmc.spindle.mapping.Mappings;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.nio.file.Files;

public abstract class GenerateAccessWidenerFromTransformerTask extends DefaultTask {
	
	@InputFile
	public abstract RegularFileProperty getProjectMappingsFile();
	
	@Input
	public abstract Property<String> getMinecraftVersion();
	
	@Input
	public abstract Property<MinecraftVersionMeta> getMinecraftVersionMeta();
	
	@InputFile
	@Optional
	public abstract RegularFileProperty getAccessWidenerPath();
	
	@InputFile
	@Optional
	public abstract RegularFileProperty getAccessTransformerPath();
	
	@Input
	@Optional
	public abstract Property<Boolean> getOverwriteAccessWidener();
	
	@TaskAction
	public void generateAccessWidenerFromTransformer() throws IOException {
		if (!getAccessTransformerPath().isPresent())
			throw new AssertionError("accessTransformerPath not set in build.gradle!");
		else if (!getAccessWidenerPath().isPresent())
			throw new AssertionError("accessWidenerPath not set in build.gradle!");
		
		final boolean overwriteWidener = getOverwriteAccessWidener().getOrElse(false);
		
		final Mappings mappings = new Mappings(getProjectMappingsFile().get().getAsFile(), getMinecraftVersion().get(), getMinecraftVersionMeta().get());
		
		final File widenerFile = getAccessWidenerPath().get().getAsFile();
		final File transformerFile = getAccessTransformerPath().get().getAsFile();
		
		final File tempFile = Files.createTempFile("spindle", ".accesswidener").toFile();
		tempFile.deleteOnExit();
		
		final BufferedReader widenerReader;
		final BufferedReader transformerReader = new BufferedReader(new FileReader(transformerFile));
		final BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempFile));
		
		String line;
		
		if (overwriteWidener) {
			widenerReader = null;
			
			tempWriter.write("accessWidener v2 named");
			tempWriter.newLine();
			tempWriter.newLine();
			tempWriter.write("# spindle {");
			tempWriter.newLine();
		} else {
			widenerReader = new BufferedReader(new FileReader(widenerFile));
			
			while ((line = widenerReader.readLine()) != null) {
				tempWriter.write(line);
				tempWriter.newLine();
				if (line.matches("^\\s*#\\s*spindle\\s*\\{\\s*$"))
					break;
			}
			if (line == null)
				throw new AssertionError("No \"# spindle {\" block found!");
		}
		
		while ((line = transformerReader.readLine()) != null) {
			if (line.startsWith("#")) { // keep AT comments
				tempWriter.write(line);
				tempWriter.newLine();
				
				continue;
			} else if (line.isBlank()) {
				continue;
			}
			
			tempWriter.write("# "); // insert AT line for reference and debugging
			tempWriter.write(line);
			tempWriter.newLine();
			
			// TODO: rewrite below logic to take existing state 
			//  in to account (right now it's potentially wasteful)
			
			final String content;
			final int endParseIndex = line.indexOf('#');
			if (endParseIndex == -1)
				content = line.strip();
			else
				content = line.substring(0, endParseIndex).strip();
			
			final String[] tokens = content.split("\\s+");
			
			final int finalModIndex = tokens[0].length() - 2;
			final String visibility;
			final boolean unfinal;
			if ((unfinal = tokens[0].endsWith("-f")) || tokens[0].endsWith("+f")) {
				visibility = tokens[0].substring(0, finalModIndex);
			} else {
				visibility = tokens[0];
			}
			
			final String className = tokens[1].replaceAll("\\.", "/");
			
			if (tokens.length == 2) { // target is a class; cease parsing
				tempWriter.write("transitive-");
				if (unfinal)
					tempWriter.write("extendable ");
				else if (!visibility.equals("private"))
					tempWriter.write("accessible ");
				
				tempWriter.write("class ");
				tempWriter.write(className);
				tempWriter.newLine();
				
				continue;
			}
			
			final MappingTree.ClassMapping classMapping = mappings.findClass(className, Mappings.Namespace.MERGED);
			
			final int methodDescIndex = tokens[2].indexOf('(');
			if (methodDescIndex == -1) { // field
				final MappingTree.FieldMapping fieldMapping = mappings.findField(classMapping, tokens[2], Mappings.Namespace.SRG);
				
				final String mappedName = fieldMapping.getName(Mappings.Namespace.MERGED.name);
				final String mappedDesc = fieldMapping.getDesc(Mappings.Namespace.MERGED.name);
				
				final String suffix = " field %s %s %s".formatted(className, mappedName, mappedDesc);
				
				if (unfinal) {
					tempWriter.write("transitive-mutable");
					tempWriter.write(suffix);
					tempWriter.newLine();
				}
				if (!visibility.equals("private")) {
					tempWriter.write("transitive-accessible");
					tempWriter.write(suffix);
					tempWriter.newLine();
				}
			} else { // method
				final String methodName = tokens[2].substring(0, methodDescIndex);
				final String mappedDesc = tokens[2].substring(methodDescIndex);
				final String methodDesc = mappings.mapSignature(mappedDesc, Mappings.Namespace.MERGED, Mappings.Namespace.SRG);
				final MappingTree.MethodMapping methodMapping = mappings.findMethod(classMapping, methodName, methodDesc, Mappings.Namespace.SRG);
				
				final String mappedName = methodMapping.getName(Mappings.Namespace.MERGED.name);
//				final String mappedDesc = methodMapping.getDesc(Mappings.Namespace.MERGED.name);
				
				final String suffix = " method %s %s %s".formatted(className, mappedName, mappedDesc);
				
				if (unfinal) {
					tempWriter.write("transitive-extendable");
					tempWriter.write(suffix);
					tempWriter.newLine();
				}
				if (visibility.equals("public") || (!unfinal && !visibility.equals("private"))) {
					tempWriter.write("transitive-accessible");
					tempWriter.write(suffix);
					tempWriter.newLine();
				}
			}
		}
		
		if (widenerReader != null) {
			var foundEnd = false;
			
			while ((line = widenerReader.readLine()) != null) {
				if (line.matches("^\\s*#\\s*}")) {
					foundEnd = true;
					do {
						tempWriter.write(line);
						tempWriter.newLine();
					} while ((line = widenerReader.readLine()) != null);
					break;
				}
			}
			
			widenerReader.close();
			
			if (!foundEnd)
				throw new AssertionError("No \"# }\" block found!");
		} else {
			tempWriter.write("# }");
			tempWriter.newLine();
		}
		
		tempWriter.flush();
		tempWriter.close();
		transformerReader.close();
		
		final BufferedReader tempReader = new BufferedReader(new FileReader(tempFile));
		final BufferedWriter widenerWriter = new BufferedWriter(new FileWriter(widenerFile));
		tempReader.transferTo(widenerWriter);
		widenerWriter.flush();
		tempReader.close();
		widenerWriter.close();
	}
	
}
