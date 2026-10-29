package de.local.tapoviewer;

import android.content.Context;
import android.graphics.*;
import android.widget.Button;

/** Real TV button semantics and focus behavior, with a crisp font-independent icon. */
final class IconButton extends Button {
    private final String icon;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    IconButton(Context context,String icon){super(context);this.icon=icon; ViewerStyle.button(this); setText("");}
    // TextView's single-line positioning must never scroll a canvas-drawn icon off-screen.
    @Override public void scrollTo(int x,int y){super.scrollTo(0,0);}
    @Override protected void onDraw(Canvas canvas){
        float size=Math.min(ViewerStyle.dp(getContext(),25),Math.min(getWidth(),getHeight())*0.65f);
        canvas.save();canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);
        paint.setColor(isEnabled()?getCurrentTextColor():ViewerStyle.MUTED);
        String kind=icon.equals("record")&&getText().toString().equals("■")?"stop":icon;
        if(kind.equals("record")&&isEnabled()&&!isFocused()&&!isSelected())paint.setColor(Color.rgb(255,103,120));
        StreamIcon.draw(canvas,paint,kind,isSelected()||getText().toString().equals("♥"));canvas.restore();
    }
}
