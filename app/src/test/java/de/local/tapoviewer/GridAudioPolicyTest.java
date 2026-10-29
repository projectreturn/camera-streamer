package de.local.tapoviewer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class GridAudioPolicyTest {
    @Test public void offAndInvisibleCameraAlwaysRemainSilent(){List<String> visible=Arrays.asList("a","b");assertEquals("",GridAudioPolicy.owner("off",visible,"a"));assertEquals("",GridAudioPolicy.owner("camera:c",visible,"a"));assertEquals("",GridAudioPolicy.owner("focus",visible,"c"));}
    @Test public void focusAndFixedModesResolveOneOwner(){List<String> visible=Arrays.asList("a","b");assertEquals("a",GridAudioPolicy.owner("focus",visible,"a"));assertEquals("b",GridAudioPolicy.owner("focus",visible,"b"));assertEquals("a",GridAudioPolicy.owner("camera:a",visible,"b"));assertEquals("",GridAudioPolicy.owner("camera:a",Collections.emptyList(),"a"));}
}
