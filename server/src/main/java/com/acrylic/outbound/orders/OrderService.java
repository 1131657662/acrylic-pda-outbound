package com.acrylic.outbound.orders;

import com.acrylic.outbound.inventory.master.MutationAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OrderService {
    private final MutationAccess access;
    private final OrderStock stock;
    private final OrderWriter writer;
    private final OrderReceipts receipts;
    private final OrderPublishing publishing;
    public OrderService(MutationAccess access,OrderStock stock,OrderWriter writer,OrderReceipts receipts,OrderPublishing publishing) {
        this.access=access;this.stock=stock;this.writer=writer;this.receipts=receipts;this.publishing=publishing;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderViews.Receipt create(MutationAccess.Context context,OrderCommands.Plan plan) {
        var locked=access.lock(context,"order:write");
        var ticket=receipts.prepare(context,locked.actor(),new OrderReceipts.Payload("create",null,plan));
        if(ticket.replay()!=null) return ticket.replay();
        OrderCommands.total(plan.lines());
        var products=stock.validate(context.warehouse(),locked.baseline(),plan);
        UUID order=writer.create(context.warehouse(),locked.actor());
        writer.revise(new OrderWriter.Work(context.warehouse(),locked.actor(),order,1,1,"创建订单"),plan,products);
        return receipts.save(ticket,context,new OrderReceipts.Result(order,0,1,1,"DRAFT","ORDER_CREATED","创建订单"));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderViews.Receipt revise(MutationAccess.Context context,UUID order,OrderCommands.Revision input) {
        var locked=access.lock(context,"order:write");
        var head=writer.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,locked.actor(),new OrderReceipts.Payload("revise",order,input));
        if(ticket.replay()!=null) return ticket.replay();
        head.requireUnassigned(input.expectedVersion());
        var policy=head.status().equals("READY")?publishing.prepare(context.warehouse(),input.expectedPolicyVersion()):null;
        OrderCommands.total(input.plan().lines());
        var products=stock.validate(context.warehouse(),locked.baseline(),input.plan());
        int revision=Math.addExact(head.revision(),1); long version=Math.addExact(Long.parseLong(head.rowVersion()),1);
        writer.revise(new OrderWriter.Work(context.warehouse(),locked.actor(),order,revision,version,input.reason().strip()),input.plan(),products);
        if(policy!=null) publishing.publish(new OrderPublishing.Work(context.warehouse(),order,locked.actor(),context.epoch(),input.reason().strip()),policy);
        return receipts.save(ticket,context,new OrderReceipts.Result(order,head.revision(),revision,version,head.status(),
            policy==null?"ORDER_REVISED":"ORDER_REVISED_PUBLISHED",input.reason().strip()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderViews.Receipt cancel(MutationAccess.Context context,UUID order,OrderCommands.Cancellation input) {
        var locked=access.lock(context,"order:cancel");
        var head=writer.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,locked.actor(),new OrderReceipts.Payload("cancel",order,input));
        if(ticket.replay()!=null) return ticket.replay();
        head.requireUnassigned(input.expectedVersion());
        writer.cancel(head);
        if(head.status().equals("READY")) publishing.cancel(new OrderPublishing.Work(context.warehouse(),order,locked.actor(),context.epoch(),input.reason().strip()));
        return receipts.save(ticket,context,new OrderReceipts.Result(order,head.revision(),head.revision(),
            Math.addExact(Long.parseLong(head.rowVersion()),1),"CANCELLED","ORDER_CANCELLED",input.reason().strip()));
    }
}
