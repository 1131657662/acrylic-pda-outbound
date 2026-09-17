package com.acrylic.outbound.pairing;

import com.acrylic.outbound.common.BusinessException;
import com.github.benmanes.caffeine.cache.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class PairingThrottle {
    private final Cache<String,AtomicInteger> counters=Caffeine.newBuilder().maximumSize(20000)
        .expireAfterWrite(Duration.ofMinutes(10)).build();
    public void take(String scope,String principal,int maximum) {
        if(counters.get(scope+":"+principal,key->new AtomicInteger()).incrementAndGet()>maximum)
            throw new BusinessException(429,"PAIRING_RATE_LIMIT","登记尝试过于频繁，请10分钟后重试原申请");
    }
}
