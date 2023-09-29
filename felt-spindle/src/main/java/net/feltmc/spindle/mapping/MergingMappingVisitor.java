package net.feltmc.spindle.mapping;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MergingMappingVisitor implements MappingVisitor {
	
	private static List<String> getAllNamespaces(Collection<MappingTree> sources) {
		return sources
			.stream()
			.flatMap(source -> source.getDstNamespaces().stream())
			.collect(Collectors.toUnmodifiableSet())
			.stream()
			.toList();
	}
	
	@NotNull
	private static MemoryMappingTree getMemoryMappingTree(String srcNamespace, Collection<MappingTree> sources, List<String> dstNamespaces) throws IOException {
		final MemoryMappingTree tree = new MemoryMappingTree();
		tree.visitHeader();
		tree.visitNamespaces(srcNamespace, dstNamespaces);
		tree.visitContent();
		
		for (var source : sources) {
			final Map<Integer, Integer> namespaceMap = source
				.getDstNamespaces()
				.stream()
				.collect(Collectors.toMap(source::getNamespaceId, tree::getNamespaceId));
			
			source.accept(new MergingMappingVisitor(tree, namespaceMap));
		}
		
		return tree;
	}
	
	public static MemoryMappingTree merge(String srcNamespace, Collection<MappingTree> sources) throws IOException {
		final List<String> dstNamespaces = getAllNamespaces(sources);
		
		final MemoryMappingTree tree = getMemoryMappingTree(srcNamespace, sources, dstNamespaces);
		
		tree.visitEnd();
		
		return tree;
	}
	
	public static MemoryMappingTree merge(String srcNamespace, List<MappingTree> sources, 
										  String mergedNamespace, List<String> mergePriority) throws IOException {
		final List<String> dstNamespaces =
			Stream.concat(getAllNamespaces(sources).stream(), Stream.of(mergedNamespace))
				.collect(Collectors.toSet())
				.stream().toList();
		
		final MemoryMappingTree tree = getMemoryMappingTree(srcNamespace, sources, dstNamespaces);
		
		final int mergedNamespaceId = tree.getNamespaceId(mergedNamespace);
		
		tree.accept(new MappingVisitor() {
			private @NotNull String tryGetName(MappingTree.ElementMapping mapping) {
				String mergedName = null;
				
				for (final String namespace : mergePriority) {
					mergedName = mapping.getName(namespace);
					if (mergedName != null)
						break;
				}
				
				return mergedName != null ? mergedName : mapping.getSrcName();
			}
			
			@Override
			public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {}
			
			private @Nullable MappingTree.ClassMapping visitingClass = null;
			@Override
			public boolean visitClass(String srcName) throws IOException {
				visitingClass = tree.getClass(srcName);
				final String mergedName = tryGetName(visitingClass);
				
				if (tree.visitClass(srcName)) {
					tree.visitDstName(MappedElementKind.CLASS, mergedNamespaceId, mergedName);
					
					return true;
				}
				
				return false;
			}
			
			@Override
			public boolean visitField(String srcName, String srcDesc) throws IOException {
				assert visitingClass != null;
				final String mergedName = tryGetName(visitingClass.getField(srcName, srcDesc));
				
				if (tree.visitField(srcName, srcDesc)) {
					tree.visitDstName(MappedElementKind.FIELD, mergedNamespaceId, mergedName);
					
					return true;
				}
				
				return false;
			}
			
			private @Nullable MappingTree.MethodMapping visitingMethod = null;
			@Override
			public boolean visitMethod(String srcName, String srcDesc) throws IOException {
				assert visitingClass != null;
				visitingMethod = visitingClass.getMethod(srcName, srcDesc);
				final String mergedName = tryGetName(visitingMethod);
				
				if (tree.visitMethod(srcName, srcDesc)) {
					tree.visitDstName(MappedElementKind.METHOD, mergedNamespaceId, mergedName);
					
					return true;
				}
				
				return false;
			}
			
			@Override
			public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) throws IOException {
				assert visitingMethod != null;
				final String mergedName = tryGetName(visitingMethod.getArg(argPosition, lvIndex, srcName));
				
				//noinspection ConstantValue
				if (tree.visitMethodArg(argPosition, lvIndex, srcName)) {
					tree.visitDstName(MappedElementKind.METHOD_ARG, mergedNamespaceId, mergedName);
					
					return true;
				}
				
				return false;
			}
			
			@Override
			public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) throws IOException {
				assert visitingMethod != null;
				final String mergedName = tryGetName(visitingMethod.getVar(lvtRowIndex, lvIndex, startOpIdx, srcName));
				
				//noinspection ConstantValue
				if (tree.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, srcName)) {
					tree.visitDstName(MappedElementKind.METHOD_VAR, mergedNamespaceId, mergedName);
					
					return true;
				}
				
				return false;
			}
			
			@Override
			public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {}
			
			@Override
			public void visitComment(MappedElementKind targetKind, String comment) throws IOException {}
		});
		
		tree.visitEnd();
		
		return tree;
	}
	
	
	private final MappingVisitor target;
	
	private final Map<Integer, Integer> namespaceMap;
	
	private MergingMappingVisitor(MappingVisitor target, Map<Integer, Integer> namespaceMap) {
		this.target = target;
		this.namespaceMap = namespaceMap;
	}
	
	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {}
	
	@Override
	public boolean visitClass(String srcName) throws IOException {
		return target.visitClass(srcName);
	}
	
	@Override
	public boolean visitField(String srcName, String srcDesc) throws IOException {
		return target.visitField(srcName, srcDesc);
	}
	
	@Override
	public boolean visitMethod(String srcName, String srcDesc) throws IOException {
		return target.visitMethod(srcName, srcDesc);
	}
	
	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) throws IOException {
		return target.visitMethodArg(argPosition, lvIndex, srcName);
	}
	
	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) throws IOException {
		return target.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, srcName);
	}
	
	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
		target.visitDstName(targetKind, namespaceMap.get(namespace), name);
	}
	
	@Override
	public void visitComment(MappedElementKind targetKind, String comment) throws IOException {}
	
}
