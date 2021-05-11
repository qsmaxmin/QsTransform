package com.qsmaxmin.plugin.model;

/**
 * @CreateBy qsmaxmin
 * @Date 2021/5/11 10:25
 * @Description
 */
public class DataHolder<K, V> {
    public K key;
    public V value;

    public DataHolder(K k, V v) {
        this.key = k;
        this.value = v;
    }
}
