package com.nageoffer.ai.ragent.core.cache;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BloomFilterPenetrationGuardTest {

    @Test
    void initializesAndDelegatesToRedissonBloomFilter() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
        when(redissonClient.<String>getBloomFilter("ragent:bloom:knowledge")).thenReturn(bloomFilter);
        when(bloomFilter.tryInit(1000L, 0.01D)).thenReturn(true);
        when(bloomFilter.contains("doc-1")).thenReturn(true);
        BloomFilterProperties properties = new BloomFilterProperties();
        properties.setEnabled(true);
        properties.setName("ragent:bloom:knowledge");
        properties.setExpectedInsertions(1000L);
        properties.setFalseProbability(0.01D);

        BloomFilterPenetrationGuard guard = new BloomFilterPenetrationGuard(redissonClient, properties);
        guard.initialize();

        assertThat(guard.mightContain("doc-1")).isTrue();
        guard.put("doc-2");
        verify(bloomFilter).tryInit(1000L, 0.01D);
        verify(bloomFilter).add("doc-2");
    }

    @Test
    void disabledGuardAllowsLookupWithoutRedis() {
        BloomFilterProperties properties = new BloomFilterProperties();
        properties.setEnabled(false);
        BloomFilterPenetrationGuard guard = new BloomFilterPenetrationGuard((RedissonClient) null, properties);

        assertThat(guard.mightContain("any")).isTrue();
        guard.put("any");
    }
}
