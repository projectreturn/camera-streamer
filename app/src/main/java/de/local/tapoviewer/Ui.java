package de.local.tapoviewer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

final class Ui {
    static int dp(Context c, int n) { return ViewerStyle.dp(c, n); }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w,h); }
    static TextView text(Context c, String s, int size, int color) {
        TextView v = new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(color); return v;
    }
    static TextView heading(Context c, String s) {
        TextView v = text(c,s,20,ViewerStyle.ACCENT); v.setTypeface(null,Typeface.BOLD); return v;
    }
    static Button button(Context c, String s, Runnable action) {
        String icon=StreamIcon.forLabel(s);
        Button v = icon==null?new Button(c):new IconButton(c,icon); v.setText(s); ViewerStyle.button(v); v.setOnClickListener(w -> action.run()); return v;
    }
    static Button iconButton(Context c,String icon,String description,Runnable action){Button v=new IconButton(c,icon);v.setContentDescription(description);v.setOnClickListener(w->action.run());return v;}
    static void add(LinearLayout parent, View child, int height) {
        LinearLayout.LayoutParams p = lp(-1, height < 0 ? height : dp(parent.getContext(),height));
        p.bottomMargin = dp(parent.getContext(),10); parent.addView(child,p);
    }
    static EditText field(LinearLayout parent, String label, String value, boolean password) {
        Context c = parent.getContext(); add(parent,text(c,label,13,ViewerStyle.MUTED),-2);
        EditText v = new EditText(c); v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(16);
        v.setSingleLine(true); v.setSelectAllOnFocus(true);
        v.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (password ? android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        v.setBackgroundResource(R.drawable.focusable_panel); add(parent,v,44); return v;
    }
    static Spinner select(LinearLayout parent, String label, String value, String... values) {
        Context c = parent.getContext(); add(parent,text(c,label,13,ViewerStyle.MUTED),-2);
        Spinner s = new Spinner(c,Spinner.MODE_DIALOG); s.setPrompt(label); ArrayAdapter<String> adapter = new ArrayAdapter<>(c,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter);
        for(int i=0;i<values.length;i++) if(values[i].equals(value)) s.setSelection(i);
        s.setBackgroundResource(R.drawable.focusable_panel); add(parent,s,42); return s;
    }
    static Switch toggle(LinearLayout parent, String label, boolean checked) {
        Switch s = new Switch(parent.getContext()); s.setText(label); s.setTextSize(15); s.setTextColor(Color.WHITE); s.setChecked(checked);
        s.setThumbTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{ViewerStyle.ACCENT,ViewerStyle.MUTED}));
        add(parent,s,42); return s;
    }
    static LinearLayout page(Activity a, String title) {
        LinearLayout root = column(a); root.setPadding(dp(a,24),dp(a,18),dp(a,24),dp(a,20)); ViewerStyle.background(root);
        LinearLayout header = row(a); Button back = button(a,"‹",a::finish); back.setContentDescription("Zurück"); header.addView(back,lp(dp(a,46),dp(a,42)));
        TextView text = text(a,"  " + title,24,Color.WHITE); text.setTypeface(null,Typeface.BOLD);
        header.addView(text,lp(0,dp(a,52))); ((LinearLayout.LayoutParams)text.getLayoutParams()).weight=1;
        add(root,header,60); return root;
    }
    static void toast(Context c, String value) { new android.os.Handler(android.os.Looper.getMainLooper()).post(()->Toast.makeText(c,value,Toast.LENGTH_LONG).show()); }
}
