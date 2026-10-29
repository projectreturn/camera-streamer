package de.local.tapoviewer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CameraRepository {
    private static final String PREFS = "camera_configuration";
    private final SharedPreferences prefs;
    private final Context context;

    public CameraRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Camera> loadCameras() {
        List<Camera> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("cameras", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String ip = item.optString("ip", "");
                if (ip.isEmpty()) continue;
                Camera camera = new Camera(
                        ip,
                        item.optString("name", "Kamera " + ip.substring(ip.lastIndexOf('.') + 1)),
                        item.optString("description", "Gespeicherte Kamera"),
                        item.optBoolean("enabled", true));
                camera.id = item.optString("id", ip);
                camera.favorite = item.optBoolean("favorite", false);
                camera.brand = item.optString("brand", "Tapo");
                camera.protocol = item.optString("protocol", "RTSP");
                camera.port = item.optInt("port", 554);
                camera.mainPath = item.optString("mainPath", "/stream1");
                camera.subPath = item.optString("subPath", "/stream2");
                camera.transport = item.optString("transport", "TCP");
                camera.codec = item.optString("codec", "Auto");
                camera.lowLatency = item.optBoolean("lowLatency", true);
                camera.onvifUrl = item.optString("onvifUrl", "");
                camera.profileToken = item.optString("profileToken", "");
                camera.ptzUrl = item.optString("ptzUrl", "");
                camera.gridStream=item.optInt("gridStream",2);camera.fullStream=item.optInt("fullStream",1);
                camera.aspectMode=item.optInt("aspectMode",0);camera.muted=item.optBoolean("muted",true);camera.volume=item.optInt("volume",70);
                camera.playbackUrl = new SecureCredentialsStore(context).loadSecret(camera.id + "_url");
                camera.subPlaybackUrl = new SecureCredentialsStore(context).loadSecret(camera.id + "_suburl");
                result.add(camera);
            }
        } catch (Exception ignored) { }
        return result;
    }

    public synchronized void saveCameras(List<Camera> cameras) {
        JSONArray array = new JSONArray();
        try {
            for (Camera camera : cameras) {
                JSONObject item = new JSONObject();
                item.put("ip", camera.ip);
                item.put("name", camera.name);
                item.put("description", camera.description);
                item.put("enabled", camera.enabled);
                item.put("id", camera.id);
                item.put("favorite", camera.favorite);
                item.put("brand", camera.brand);
                item.put("protocol", camera.protocol);
                item.put("port", camera.port);
                item.put("mainPath", camera.mainPath);
                item.put("subPath", camera.subPath);
                item.put("transport", camera.transport);
                item.put("codec", camera.codec);
                item.put("lowLatency", camera.lowLatency);
                item.put("onvifUrl", camera.onvifUrl);
                item.put("profileToken", camera.profileToken);
                item.put("ptzUrl", camera.ptzUrl);
                item.put("gridStream",camera.gridStream);item.put("fullStream",camera.fullStream);item.put("aspectMode",camera.aspectMode);item.put("muted",camera.muted);item.put("volume",camera.volume);
                array.put(item);
            }
        } catch (Exception ignored) { }
        prefs.edit().putString("cameras", array.toString()).apply();
    }

    public boolean isSetupComplete() {
        return prefs.getBoolean("setup_complete", false);
    }

    public void setSetupComplete(boolean complete) {
        prefs.edit().putBoolean("setup_complete", complete).apply();
    }

    public int getTileCount() {
        return prefs.getInt("tile_count", 4);
    }

    public void setTileCount(int count) {
        prefs.edit().putInt("tile_count", count).apply();
    }

    public Camera find(String id) {
        for (Camera camera : loadCameras()) if (camera.id.equals(id) || camera.ip.equals(id)) return camera;
        return null;
    }

    public void upsert(Camera camera) {
        List<Camera> list = loadCameras();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(camera.id)) { list.set(i, camera); saveCameras(list); return; }
        }
        list.add(camera);
        saveCameras(list);
    }

    public void remove(String id) {
        List<Camera> list = loadCameras();
        list.removeIf(camera -> camera.id.equals(id));
        saveCameras(list);
        new SecureCredentialsStore(context).remove(id);
        clearViewport(id);
        try{JSONObject all=new JSONObject(option("positions","{}"));all.remove(id);setOption("positions",all.toString());}catch(Exception ignored){}
    }

    public boolean keepAwake() { return prefs.getBoolean("keep_awake", true); }
    public void keepAwake(boolean value) { prefs.edit().putBoolean("keep_awake", value).apply(); }
    public boolean recover() { return prefs.getBoolean("recover", true); }
    public void recover(boolean value) { prefs.edit().putBoolean("recover", value).apply(); }
    public boolean favoritesOnly() { return prefs.getBoolean("favorites_only", false); }
    public void favoritesOnly(boolean value) { prefs.edit().putBoolean("favorites_only", value).apply(); }
    public boolean autoScroll() { return prefs.getBoolean("auto_scroll", false); }
    public void autoScroll(boolean value) { prefs.edit().putBoolean("auto_scroll", value).apply(); }
    public int scrollSeconds() { return Math.max(5, Math.min(120, prefs.getInt("scroll_seconds", 15))); }
    public void scrollSeconds(int seconds) { prefs.edit().putInt("scroll_seconds", Math.max(5, Math.min(120, seconds))).apply(); }
    public boolean streamInfoEnabled() { return prefs.getBoolean("stream_info_enabled",true); }
    public void streamInfoEnabled(boolean enabled) { prefs.edit().putBoolean("stream_info_enabled",enabled).apply(); }
    public int streamInfoSeconds() { int value=prefs.getInt("stream_info_seconds",5); return value==0?0:Math.max(2,Math.min(60,value)); }
    public void streamInfoSeconds(int seconds) { prefs.edit().putInt("stream_info_seconds",seconds==0?0:Math.max(2,Math.min(60,seconds))).apply(); }
    public String option(String key,String fallback){return prefs.getString("option_"+key,fallback);}
    public long recordingDurationMs(){try{return Math.max(5,Math.min(60,Integer.parseInt(option("record_minutes","15"))))*60000L;}catch(Exception e){return 900000L;}}
    public void setOption(String key,String value){prefs.edit().putString("option_"+key,value).apply();}
    public String settingsJson(){JSONObject all=new JSONObject();try{for(java.util.Map.Entry<String,?> e:prefs.getAll().entrySet())all.put(e.getKey(),e.getValue());}catch(Exception e){throw new IllegalStateException(e);}return all.toString();}
    public void move(String id,int direction){List<Camera> list=loadCameras();for(int i=0;i<list.size();i++)if(list.get(i).id.equals(id)){int target=Math.max(0,Math.min(list.size()-1,i+direction));java.util.Collections.swap(list,i,target);saveCameras(list);return;}}
    public JSONArray views(){try{return new JSONArray(option("views","[]"));}catch(Exception e){return new JSONArray();}}
    public void saveView(String name,List<String> ids,int layout,boolean favorites){try{JSONArray all=views();JSONObject v=new JSONObject();v.put("name",name);v.put("ids",new JSONArray(ids));v.put("layout",layout);v.put("favorites",favorites);for(int i=all.length()-1;i>=0;i--)if(all.getJSONObject(i).optString("name").equals(name))all.remove(i);if(all.length()>=30)throw new IllegalArgumentException("Maximal 30 Ansichten");all.put(v);setOption("views",all.toString());}catch(org.json.JSONException e){throw new IllegalStateException(e);}}
    public List<Camera> liveCameras(){List<Camera> list=CameraPages.visible(loadCameras(),favoritesOnly());String selected=option("selected_view","");if(selected.isEmpty())return list;try{JSONArray views=views();for(int i=0;i<views.length();i++){JSONObject view=views.getJSONObject(i);if(!view.optString("name").equals(selected))continue;JSONArray ids=view.getJSONArray("ids");List<Camera> result=new ArrayList<>();for(int n=0;n<ids.length();n++)for(Camera c:list)if(c.id.equals(ids.getString(n)))result.add(c);return result;}}catch(Exception ignored){}return list;}
    public void selectView(String name){setOption("selected_view",name);try{JSONArray all=views();for(int i=0;i<all.length();i++){JSONObject v=all.getJSONObject(i);if(v.optString("name").equals(name)){setTileCount(v.optInt("layout",4));favoritesOnly(v.optBoolean("favorites",false));}}}catch(Exception ignored){}}
    public void saveNamedViewport(String id,String name,int zoom,float x,float y){if(name==null||name.trim().isEmpty()||name.length()>80)throw new IllegalArgumentException("Positionsname muss 1 bis 80 Zeichen haben");try{JSONObject all=new JSONObject(option("positions","{}"));JSONObject views=all.optJSONObject(id);if(views==null)views=new JSONObject();if(views.length()>=32&&!views.has(name))throw new IllegalArgumentException("Maximal 32 Positionen pro Kamera");saveViewport(id,zoom,x,y);SavedViewport saved=loadViewport(id);JSONObject view=new JSONObject();view.put("zoom",saved.zoomIndex);view.put("x",saved.x);view.put("y",saved.y);views.put(name,view);all.put(id,views);setOption("positions",all.toString());}catch(Exception e){throw new IllegalArgumentException("Position konnte nicht gespeichert werden",e);}}
    public JSONObject positions(String id){try{JSONObject all=new JSONObject(option("positions","{}"));JSONObject result=all.optJSONObject(id);return result==null?new JSONObject():result;}catch(Exception e){return new JSONObject();}}
    public void restoreSettings(JSONObject settings){SharedPreferences.Editor edit=prefs.edit();java.util.Iterator<String> keys=settings.keys();while(keys.hasNext()){String key=keys.next();Object v=settings.opt(key);if(v instanceof Boolean)edit.putBoolean(key,(Boolean)v);else if(v instanceof Number)edit.putInt(key,((Number)v).intValue());else if(v instanceof String)edit.putString(key,(String)v);}edit.apply();}
    public synchronized void saveViewport(String id,int zoomIndex,float x,float y) {
        if(id==null||id.isEmpty()||zoomIndex<0||zoomIndex>5||Float.isNaN(x)||Float.isInfinite(x)||Float.isNaN(y)||Float.isInfinite(y)) throw new IllegalArgumentException("Ungültige Bildposition");
        try {
            JSONObject all=new JSONObject(prefs.getString("camera_viewports","{}"));
            JSONObject view=new JSONObject();view.put("zoom",zoomIndex);view.put("x",Math.max(-1,Math.min(1,x)));view.put("y",Math.max(-1,Math.min(1,y)));all.put(id,view);
            prefs.edit().putString("camera_viewports",all.toString()).apply();
        } catch(org.json.JSONException e){throw new IllegalStateException("Bildposition konnte nicht gespeichert werden",e);}
    }
    public synchronized SavedViewport loadViewport(String id) {
        if(id==null) return null;
        try {
            JSONObject view=new JSONObject(prefs.getString("camera_viewports","{}")).optJSONObject(id); if(view==null)return null;
            int zoom=view.getInt("zoom");float x=(float)view.getDouble("x"),y=(float)view.getDouble("y");
            if(zoom<0||zoom>5||Float.isNaN(x)||Float.isInfinite(x)||Float.isNaN(y)||Float.isInfinite(y))return null;
            return new SavedViewport(zoom,Math.max(-1,Math.min(1,x)),Math.max(-1,Math.min(1,y)));
        } catch(Exception ignored){return null;}
    }
    public synchronized void clearViewport(String id) {
        if(id==null)return;
        try {JSONObject all=new JSONObject(prefs.getString("camera_viewports","{}"));all.remove(id);prefs.edit().putString("camera_viewports",all.toString()).apply();}catch(Exception ignored){}
    }
    public static final class SavedViewport {
        public final int zoomIndex;public final float x,y;
        SavedViewport(int zoomIndex,float x,float y){this.zoomIndex=zoomIndex;this.x=x;this.y=y;}
    }
}
