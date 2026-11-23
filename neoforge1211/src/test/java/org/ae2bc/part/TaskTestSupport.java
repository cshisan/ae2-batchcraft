package org.ae2bc.part;

import appeng.api.stacks.AEKey;
import org.ae2bc.part.task.Pp2mpTaskData;
import org.ae2bc.part.task.Pp2mpTaskRecord;
import org.junit.jupiter.api.Assertions;

import java.util.Map;

final class TaskTestSupport {
    private TaskTestSupport() {
    }

    static Pp2mpTaskRecord record(Pp2mpTaskData data) {
        Assertions.assertEquals(1, data.getTasks().size());
        return data.getTasks().values().iterator().next();
    }

    static Map<AEKey, Long> remaining(Pp2mpTaskData data) {
        return data.getTasks().isEmpty() ? Map.of() : TaskTestSupport.record(data).getProgress().remaining();
    }
}
