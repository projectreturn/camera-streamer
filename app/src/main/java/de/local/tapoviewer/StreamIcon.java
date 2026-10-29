package de.local.tapoviewer;

import android.graphics.*;

/** A consistent 24-unit line-icon set; independent of fonts, emoji and system glyphs. */
final class StreamIcon {
    static void draw(Canvas c,Paint p,String kind,boolean selected) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind) {
            case "rewind": triangle(c,p,12,5,12,19,2,12);triangle(c,p,22,5,22,19,12,12);break;
            case "forward": triangle(c,p,2,5,2,19,12,12);triangle(c,p,12,5,12,19,22,12);break;
            case "audio": path(c,p,3,9,7,9,12,4,12,20,7,15,3,15,3,9);c.drawArc(10,5,22,19,-65,130,false,p);break;
            case "position": c.drawCircle(12,12,6,p);c.drawLine(12,2,12,7,p);c.drawLine(12,17,12,22,p);c.drawLine(2,12,7,12,p);c.drawLine(17,12,22,12,p);break;
            case "heart": {
                Path path=new Path(); path.moveTo(12,20); path.cubicTo(8,17,2.8f,13.4f,2.8f,8.6f); path.cubicTo(2.8f,3.5f,9,2.3f,12,7); path.cubicTo(15,2.3f,21.2f,3.5f,21.2f,8.6f); path.cubicTo(21.2f,13.4f,16,17,12,20);
                if(selected) p.setStyle(Paint.Style.FILL); c.drawPath(path,p); break;
            }
            case "gear": {
                Path path=new Path(); for(int i=0;i<32;i++){double angle=Math.PI*2*i/32-Math.PI/2; float r=i%4==0||i%4==3?10:8.2f; float x=12+(float)Math.cos(angle)*r,y=12+(float)Math.sin(angle)*r; if(i==0)path.moveTo(x,y);else path.lineTo(x,y);} path.close(); c.drawPath(path,p); c.drawCircle(12,12,3.3f,p); break;
            }
            case "files": case "media":
                c.drawRoundRect(5,3,22,18,2.5f,2.5f,p); path(c,p,2,8,2,21,18,21); triangle(c,p,11,7,11,14,17,10.5f); break;
            case "camera":
                c.drawRoundRect(2,6,16,18,3,3,p); path(c,p,16,10,22,6,22,18,16,14); break;
            case "photo":
                c.drawRoundRect(2,6,22,20,3,3,p); path(c,p,7,6,9,3,15,3,17,6); c.drawCircle(12,13,4,p); break;
            case "play": triangle(c,p,7,4,7,20,20,12); break;
            case "playpause": triangle(c,p,3,5,3,19,12,12); c.drawLine(17,5,17,19,p); c.drawLine(22,5,22,19,p); break;
            case "previous": c.drawLine(4,5,4,19,p); triangle(c,p,19,5,19,19,8,12); break;
            case "next": c.drawLine(20,5,20,19,p); triangle(c,p,5,5,5,19,16,12); break;
            case "left": path(c,p,15,5,8,12,15,19); break;
            case "right": path(c,p,9,5,16,12,9,19); break;
            case "up": path(c,p,5,15,12,8,19,15); break;
            case "down": path(c,p,5,9,12,16,19,9); break;
            case "record": p.setStyle(Paint.Style.FILL); c.drawCircle(12,12,6.5f,p); break;
            case "stop": p.setStyle(Paint.Style.FILL); c.drawRoundRect(5,5,19,19,2,2,p); break;
            case "quality":
                c.drawRoundRect(2,4,22,20,3,3,p); p.setStyle(Paint.Style.FILL); p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); p.setTextSize(9); p.setTextAlign(Paint.Align.CENTER); c.drawText("1/2",12,15.2f,p); break;
            case "plus": c.drawLine(5,12,19,12,p); c.drawLine(12,5,12,19,p); break;
            case "zoomIn": case "zoomOut":
                c.drawCircle(10,10,7,p); c.drawLine(15.3f,15.3f,21,21,p); c.drawLine(6.5f,10,13.5f,10,p); if(kind.equals("zoomIn"))c.drawLine(10,6.5f,10,13.5f,p); break;
            case "reset":
                c.drawArc(4,4,21,21,215,300,false,p); path(c,p,3,3,3,9,9,9); break;
            case "refresh":
                c.drawArc(3,3,21,21,215,285,false,p); path(c,p,20,2,20,8,14,8); break;
            case "edit":
                path(c,p,4,15,15,4,20,9,9,20,3,21,4,15); c.drawLine(12,7,17,12,p); break;
            case "trash":
                c.drawLine(3,6,21,6,p); path(c,p,5,6,6,21,18,21,19,6); path(c,p,8,6,8,3,16,3,16,6); c.drawLine(10,10,10,17,p); c.drawLine(14,10,14,17,p); break;
            case "move":
                c.drawLine(12,2,12,22,p); c.drawLine(2,12,22,12,p); path(c,p,8,6,12,2,16,6); path(c,p,8,18,12,22,16,18); path(c,p,6,8,2,12,6,16); path(c,p,18,8,22,12,18,16); break;
            case "grid":
                for(int y=3;y<=14;y+=11)for(int x=3;x<=14;x+=11)c.drawRoundRect(x,y,x+7,y+7,1.5f,1.5f,p); break;
            default: c.drawCircle(12,12,9,p); c.drawLine(12,10,12,17,p); p.setStyle(Paint.Style.FILL); c.drawCircle(12,6.5f,1,p);
        }
    }
    private static void path(Canvas c,Paint p,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);c.drawPath(path,p);}
    private static void triangle(Canvas c,Paint p,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();c.drawPath(path,p);}
    static String forLabel(String label) {
        switch(label){
            case "Ton":return "audio";case "Position":return "position";
            case "♡": case "♥": return "heart"; case "⚙": return "gear";
            case "‹": case "◀": return "left"; case "›": case "▶": return label.equals("▶")?"play":"right";
            case "▲": return "up"; case "▼": return "down";
            case "▶ / Ⅱ": return "playpause"; case "≪": return "previous"; case "≫": return "next";
            case "HD / SD": return "quality"; case "−": return "zoomOut"; case "＋": return "zoomIn";
            case "↺": return "reset"; case "▣": return "photo"; case "●": return "record";
            case "⟳": return "refresh"; case "▤": return "media"; case "PTZ": return "move";
            case "✎": return "edit"; case "⌫": return "trash"; default:return null;
        }
    }
}
