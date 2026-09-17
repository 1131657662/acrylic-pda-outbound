package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OrderReleaseService {
    private final MutationAccess access;
    private final OrderWriter writer;
    private final OrderReceipts receipts;
    private final OrderPreflight preflight;
    private final OrderPublishing publishing;
    public OrderReleaseService(MutationAccess access,OrderWriter writer,OrderReceipts receipts,OrderPreflight preflight,OrderPublishing publishing) {
        this.access=access;this.writer=writer;this.receipts=receipts;this.preflight=preflight;this.publishing=publishing;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderViews.Receipt publish(MutationAccess.Context context,UUID order,Command input) {
        var locked=access.lock(context,"order:publish");var head=writer.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,locked.actor(),new OrderReceipts.Payload("publish",order,input));
        if(ticket.replay()!=null) return ticket.replay();
        head.requireDraft(input.expectedVersion());
        var policy=publishing.prepare(context.warehouse(),input.expectedPolicyVersion());
        var check=preflight.check(context.warehouse(),order);
        if(!check.checksPassed()) throw new BusinessException(409,"ORDER_PUBLICATION_BLOCKED","库存或映射发生变化，请修订草稿并重新检查");
        long version=Math.addExact(Long.parseLong(head.rowVersion()),1);
        writer.advanceVersion(order,version);
        publishing.publish(new OrderPublishing.Work(context.warehouse(),order,locked.actor(),context.epoch(),input.reason().strip()),policy);
        return receipts.save(ticket,context,new OrderReceipts.Result(order,head.revision(),head.revision(),version,
            "READY","ORDER_PUBLISHED",input.reason().strip()));
    }
    public record Command(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,
        @NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedPolicyVersion,@NotBlank @Size(max=500) String reason) { }
}
