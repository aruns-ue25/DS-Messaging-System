package com.dsmessaging;

import com.dsmessaging.sync.HybridLogicalClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SyncTest {
    public static void main(String[] args) {
        testHlcMonotonicity();
        testHlcOrdering();
        System.out.println("All basic HLC tests passed!");
    }

    private static void testHlcMonotonicity() {
        HybridLogicalClock hlc = new HybridLogicalClock("node-1");
        HybridLogicalClock last = hlc.updateLocal();
        for (int i = 0; i < 100; i++) {
            HybridLogicalClock current = hlc.updateLocal();
            assert current.compareTo(last) > 0 : "HLC must be monotonic";
            last = current;
        }
    }

    private static void testHlcOrdering() {
        HybridLogicalClock hlc1 = new HybridLogicalClock(1000, 0, "node-1");
        HybridLogicalClock hlc2 = new HybridLogicalClock(1000, 1, "node-1");
        HybridLogicalClock hlc3 = new HybridLogicalClock(1001, 0, "node-1");

        List<HybridLogicalClock> list = new ArrayList<>();
        list.add(hlc3);
        list.add(hlc1);
        list.add(hlc2);

        Collections.sort(list);

        assert list.get(0).equals(hlc1);
        assert list.get(1).equals(hlc2);
        assert list.get(2).equals(hlc3);
    }
}
