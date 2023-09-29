package net.feltmc.spindle.util;

import java.util.HashMap;
import java.util.function.Function;

public class LazyMap<K, V> extends HashMap<K, V> {
	
	private final Function<K, V> compute;
	
	public LazyMap(Function<K, V> compute) {
		super();
		this.compute = compute;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		return computeIfAbsent((K) key, compute);
	}
	
}
