package com.acrylic.outbound.orders;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.orders.OrderTemporaryCandidateModels.*;

@Service
public class OrderTemporaryCandidateService {
    private final Access access;private final OrderQueries orders;private final OrderTemporaryCandidateQueries candidates;
    public OrderTemporaryCandidateService(Access access,OrderQueries orders,OrderTemporaryCandidateQueries candidates) {
        this.access=access;this.orders=orders;this.candidates=candidates;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Response read(UUID warehouse,UUID order,Filter filter) {
        access.require("order:read",warehouse);
        if(filter.page()<1 || filter.page()>1_000_000)
            throw new BusinessException(400,"INVALID_REQUEST","候选页码须为1至1000000");
        var detail=orders.detail(warehouse,order,null);
        if(!detail.order().source().equals("WEB"))
            throw new BusinessException(409,"FORMAL_ORDER_REQUIRED","请从正式订单核对临时单候选");
        var range=Range.resolve(filter,detail.order().createdAt());
        return new Response(order.toString(),detail.order().revision(),detail.order().rowVersion(),range,Instant.now().toString(),
            candidates.read(detail,new Query(warehouse,range,filter.page())),candidates.hasFormalWork(order),true,false);
    }
}
