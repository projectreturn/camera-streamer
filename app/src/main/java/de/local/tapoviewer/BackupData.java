package de.local.tapoviewer;

import android.content.Context;
import org.json.*;
import java.util.*;

final class BackupData {
    static String export(Context context,boolean include)throws Exception{
        CameraRepository repo=new CameraRepository(context);JSONObject result=new JSONObject();result.put("version",1);JSONObject settings=new JSONObject(repo.settingsJson());if(!settings.has("cameras"))settings.put("cameras","[]");result.put("settings",settings);JSONArray accounts=new JSONArray();SecureCredentialsStore store=new SecureCredentialsStore(context);
        for(Camera c:repo.loadCameras()){JSONObject item=new JSONObject();item.put("id",c.id);if(include){SecureCredentialsStore.Credentials auth=store.load(c.id);item.put("user",auth.username);item.put("pass",auth.password);item.put("url",c.playbackUrl);item.put("suburl",c.subPlaybackUrl);}accounts.put(item);}result.put("accounts",accounts);return result.toString();
    }
    static void restore(Context context,JSONObject backup)throws Exception{
        BackupActivity.validate(backup);CameraRepository repo=new CameraRepository(context);JSONObject settings=new JSONObject(backup.getJSONObject("settings").toString());JSONArray incoming=new JSONArray(settings.getString("cameras"));Set<String> ids=new HashSet<>();for(int i=0;i<incoming.length();i++)ids.add(incoming.getJSONObject(i).getString("id"));JSONArray existing=new JSONArray(new JSONObject(repo.settingsJson()).optString("cameras","[]"));for(int i=0;i<existing.length();i++)if(!ids.contains(existing.getJSONObject(i).getString("id")))incoming.put(existing.getJSONObject(i));settings.put("cameras",incoming.toString());SecureCredentialsStore store=new SecureCredentialsStore(context);JSONArray accounts=backup.getJSONArray("accounts");
        for(int i=0;i<accounts.length();i++){JSONObject c=accounts.getJSONObject(i);if(!c.has("user"))continue;String id=c.getString("id");store.save(id,c.getString("user"),c.getString("pass"));store.saveSecret(id+"_url",c.getString("url"));store.saveSecret(id+"_suburl",c.getString("suburl"));}settings.put("option_auto_delete","false");repo.restoreSettings(settings);repo.setSetupComplete(true);
    }
}
