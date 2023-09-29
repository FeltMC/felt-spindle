package net.feltmc.spindle.mapping;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.format.Tiny2Reader;
import net.fabricmc.mappingio.format.TsrgReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.feltmc.spindle.util.LazyMap;

import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Mappings {
	
	private static final String SRG_URL_TEMPLATE = "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/%s/joined.tsrg";
	private static final String MOJMAP_CLIENT_MAPPINGS = "client_mappings";
	private static final String MOJMAP_SERVER_MAPPINGS = "server_mappings";
	
	public enum Namespace {
		OBF("obf"),
		SRG("srg"),
		MOJMAP("mojmap"),
		INTERMEDIARY("intermediary"),
		NAMED("named"),
		MERGED("merged"),
		;
		
		public final String name;
		
		Namespace(String name) {
			this.name = name;
		}
	}
	
	public final MappingTree tree;
	public final Map<Namespace, Map<String, MappingTree.ClassMapping>> map;
	
	public Mappings(final File projectMappingsFile, final String mcVersion, final MinecraftVersionMeta mcVersionMeta) throws IOException {
		final MemoryMappingTree projectMappingsTree = new MemoryMappingTree();
		projectMappingsTree.visitHeader();
		Tiny2Reader.read(new FileReader(projectMappingsFile), projectMappingsTree);
		projectMappingsTree.visitEnd();
		
		final String srgURL = String.format(SRG_URL_TEMPLATE, mcVersion);
		final MemoryMappingTree srgMappingsTree = new MemoryMappingTree();
		srgMappingsTree.visitHeader();
		//noinspection deprecation
		TsrgReader.read(new InputStreamReader(new URL(srgURL).openStream()), srgMappingsTree);
		srgMappingsTree.visitEnd();
		
		final String mojMapClientUrl = mcVersionMeta.download(MOJMAP_CLIENT_MAPPINGS).url();
		final String mojMapServerUrl = mcVersionMeta.download(MOJMAP_SERVER_MAPPINGS).url();
		final MemoryMappingTree mojMapTree = new MemoryMappingTree();
		final MappingVisitor mojMapInverter = new MappingSourceNsSwitch(mojMapTree, Namespace.OBF.name);
		mojMapTree.visitHeader();
		//noinspection deprecation
		ProGuardReader.read(new InputStreamReader(new URL(mojMapClientUrl).openStream()), Namespace.MOJMAP.name, Namespace.OBF.name, mojMapInverter);
		//noinspection deprecation
		ProGuardReader.read(new InputStreamReader(new URL(mojMapServerUrl).openStream()), Namespace.MOJMAP.name, Namespace.OBF.name, mojMapInverter);
		mojMapTree.visitEnd();
		
		tree = MergingMappingVisitor.merge(
			Namespace.OBF.name, List.of(projectMappingsTree, srgMappingsTree, mojMapTree),
			Namespace.MERGED.name, Stream.of(Namespace.NAMED, Namespace.MOJMAP, Namespace.INTERMEDIARY).map(x -> x.name).toList());;
		map = new LazyMap<>(
			namespace ->
				tree
					.getClasses()
					.stream()
					.collect(Collectors.toMap(classMapping -> classMapping.getName(namespace.name), x -> x)));
	}
	
	public String mapSignature(String signature, Namespace from, Namespace to) {
		var builder = new StringBuilder();
		
		for (int i = 0; i < signature.length(); i++) {
			var c = signature.charAt(i);
			builder.append(c);
			
			if (c == 'L') {
				for (int j = ++i; j < signature.length(); j++) {
					if (signature.charAt(j) == ';') {
						var fromType = signature.substring(i, j);
						var mapping = map.get(from).get(fromType);
						if (mapping != null)
							builder.append(mapping.getName(to.name));
						else
							builder.append(fromType);
						
						i = j - 1;
						break;
					}
				}
			}
		}
		
		return builder.toString();
	}
	
	public MappingTree.ClassMapping findClass(String className, Namespace namespace) {
		return map.get(namespace).getOrDefault(className, null);
	}
	
	public MappingTree.FieldMapping findField(MappingTree.ClassMapping classMapping, String fieldName, String fieldDesc, Namespace namespace) {
		return classMapping.getField(fieldName, fieldDesc, tree.getNamespaceId(namespace.name));
	}
	
	public MappingTree.FieldMapping findField(MappingTree.ClassMapping classMapping, String fieldName, Namespace namespace) {
		return classMapping
			.getFields()
			.stream()
			.filter(x ->
				x.getName(namespace.name).equals(fieldName))
			.findFirst()
			.orElse(null);
	}
	
	public MappingTree.MethodMapping findMethod(MappingTree.ClassMapping classMapping, String methodName, String methodDesc, Namespace namespace) {
		return classMapping.getMethod(methodName, methodDesc, tree.getNamespaceId(namespace.name));
	}
	
}
