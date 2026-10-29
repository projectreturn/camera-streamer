package de.local.tapoviewer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;

final class ViewerStyle {
    static final int BACKGROUND = Color.rgb(5, 12, 28);
    static final int PANEL = Color.rgb(11, 20, 39);
    static final int ACCENT = Color.rgb(27, 194, 228);
    static final int MUTED = Color.rgb(150, 170, 195);

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context context, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        return shape;
    }

    static void background(View view) {
        view.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 23, 43), BACKGROUND}));
    }

    static void button(Button button) {
        button.setFocusable(true);button.setFocusableInTouchMode(true);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(button.getContext(), 10), 0, dp(button.getContext(), 10), 0);
        button.setBackgroundResource(R.drawable.focusable_panel);
        button.setTextColor(new ColorStateList(new int[][]{
                {android.R.attr.state_focused}, {android.R.attr.state_selected}, {}
        }, new int[]{BACKGROUND, BACKGROUND, Color.WHITE}));
    }
}
