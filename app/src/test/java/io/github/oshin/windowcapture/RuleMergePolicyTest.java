package io.github.oshin.windowcapture;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;
public class RuleMergePolicyTest {
    @Test public void partialNativeRecoveryKeepsOtherPagePatches(){assertFalse(RuleMergePolicy.keepPatched(true,Set.of("detail"),"detail"));assertTrue(RuleMergePolicy.keepPatched(true,Set.of("detail"),"result"));}
    @Test public void disableDropsPatchesAndUnknownPages(){assertFalse(RuleMergePolicy.keepPatched(false,Set.of(),"result"));assertFalse(RuleMergePolicy.keepPatched(true,Set.of(),""));}
}
