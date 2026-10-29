package de.local.tapoviewer;

import java.util.List;

/** Resolves exactly one visible audio owner, or silence. No hidden camera is started for audio. */
final class GridAudioPolicy {
    static String owner(String mode,List<String> visible,String focused) {
        String wanted="focus".equals(mode)?focused:mode!=null&&mode.startsWith("camera:")?mode.substring(7):"";
        return wanted!=null&&visible.contains(wanted)?wanted:"";
    }
}
