package de.local.tapoviewer;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Settings tiles use the same line icons as the compact navigation and player. */
final class ActionIconView extends View {
    private final String kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    ActionIconView(Context context,String kind){super(context);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c); float side=Math.min(getWidth(),getHeight());c.save();c.translate((getWidth()-side)/2,(getHeight()-side)/2);c.scale(side/40,side/40);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(15,40,58));c.drawRoundRect(1,1,39,39,12,12,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(0.8f);paint.setColor(Color.rgb(31,89,111));c.drawRoundRect(1,1,39,39,12,12,paint);
        c.translate(8,8);paint.setColor(ViewerStyle.ACCENT);StreamIcon.draw(c,paint,kind,false);c.restore();
    }
}
