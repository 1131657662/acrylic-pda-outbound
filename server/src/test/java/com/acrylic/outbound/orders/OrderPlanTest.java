package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrderPlanTest {
    @Test void totalUsesLongWithoutVehicleLimit() {
        var lines=List.of(new OrderCommands.Line("003",Integer.MAX_VALUE),new OrderCommands.Line("571",Integer.MAX_VALUE));
        assertThat(OrderCommands.total(lines)).isEqualTo(4294967294L);
    }
    @Test void twoLinesWithSameCodeAreNotOnePlan() {
        var lines=List.of(new OrderCommands.Line("003",1),new OrderCommands.Line("003",2));
        assertThatThrownBy(()->OrderCommands.total(lines)).isInstanceOf(BusinessException.class);
    }
}
