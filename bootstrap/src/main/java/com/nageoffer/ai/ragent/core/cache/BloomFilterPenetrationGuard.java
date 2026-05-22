package com.nageoffer.ai.ragent.core.cache;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BloomFilterPenetrationGuard {

    private final RedissonClient redissonClient;
    private final BloomFilterProperties properties;
    private RBloomFilter<String> bloomFilter;

    public BloomFilterPenetrationGuard(ObjectProvider<RedissonClient> redissonClientProvider,
                                       BloomFilterProperties properties) {
        this(redissonClientProvider == null ? null : redissonClientProvider.getIfAvailable(), properties);
    }

    BloomFilterPenetrationGuard(RedissonClient redissonClient, BloomFilterProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties == null ? new BloomFilterProperties() : properties;
    }

    @PostConstruct
    public void initialize() {
        if (!properties.enabled() || redissonClient == null) {
            return;
        }
        try {
            bloomFilter = redissonClient.getBloomFilter(StrUtil.blankToDefault(properties.getName(), "ragent:bloom:default"));
            bloomFilter.tryInit(properties.safeExpectedInsertions(), properties.safeFalseProbability());
        } catch (RuntimeException ex) {
            bloomFilter = null;
            log.warn("RBloomFilter initialization failed, cache penetration guard will allow lookups", ex);
        }
    }

    public boolean mightContain(String key) {
        if (StrUtil.isBlank(key) || bloomFilter == null) {
            return true;
        }
        return bloomFilter.contains(key);
    }

    public void put(String key) {
        if (StrUtil.isBlank(key) || bloomFilter == null) {
            return;
        }
        bloomFilter.add(key);
    }
}
