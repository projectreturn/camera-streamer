package de.local.tapoviewer;

import android.content.Context;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.security.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

final class OnvifClient {
    static final String DEVICE="http://www.onvif.org/ver10/device/wsdl";
    static final String MEDIA="http://www.onvif.org/ver10/media/wsdl";
    static final String PTZ="http://www.onvif.org/ver20/ptz/wsdl";
    static final class Profile {
        String token,name,uri,ptzUrl,deviceUrl;
    }
    static List<Camera> discover(Context context) throws Exception {
        List<Camera> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        android.net.wifi.WifiManager.MulticastLock lock=wifi==null?null:wifi.createMulticastLock("camera-streamer-onvif");
        if(lock!=null) lock.acquire();
        try(DatagramSocket socket=new DatagramSocket()) {
            socket.setSoTimeout(800);
            String xml="<e:Envelope xmlns:e='http://www.w3.org/2003/05/soap-envelope' xmlns:a='http://schemas.xmlsoap.org/ws/2004/08/addressing' xmlns:d='http://schemas.xmlsoap.org/ws/2005/04/discovery' xmlns:dn='http://www.onvif.org/ver10/network/wsdl'><e:Header><a:MessageID>uuid:"+UUID.randomUUID()+"</a:MessageID><a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To><a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action></e:Header><e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>";
            byte[] request=xml.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(request,request.length,InetAddress.getByName("239.255.255.250"),3702));
            long until=System.currentTimeMillis()+4500;
            while(System.currentTimeMillis()<until) {
                byte[] bytes=new byte[65507]; DatagramPacket packet=new DatagramPacket(bytes,bytes.length);
                try { socket.receive(packet); } catch(SocketTimeoutException e) { continue; }
                Document doc=parse(new String(bytes,0,packet.getLength(),StandardCharsets.UTF_8));
                NodeList addresses=doc.getElementsByTagNameNS("*","XAddrs");
                for(int i=0;i<addresses.getLength();i++) for(String address:addresses.item(i).getTextContent().trim().split("\\s+")) {
                    URI uri=URI.create(address); String host=uri.getHost();
                    if(host==null || !seen.add(host)) continue;
                    Camera c=new Camera(host,"ONVIF "+host,"ONVIF-Gerät erkannt",true);
                    c.protocol="ONVIF"; c.brand="Generic ONVIF"; c.onvifUrl=address; result.add(c);
                }
            }
        } finally { if(lock!=null && lock.isHeld()) lock.release(); }
        return result;
    }
    static List<Profile> profiles(Camera camera,SecureCredentialsStore.Credentials auth) throws Exception {
        List<String> candidates=new ArrayList<>();
        if(!camera.onvifUrl.isEmpty()) candidates.add(camera.onvifUrl);
        else { candidates.add("http://"+camera.ip+":2020/onvif/device_service"); candidates.add("http://"+camera.ip+"/onvif/device_service"); }
        Exception failure=null;
        for(String device:candidates) try {
            Document caps=soap(device,DEVICE+"/GetCapabilities","<d:GetCapabilities xmlns:d='"+DEVICE+"'><d:Category>All</d:Category></d:GetCapabilities>",auth);
            String media=address(caps,"Media"),ptz=address(caps,"PTZ");
            if(media.isEmpty()) throw new IOException("Media service missing");
            Document list=soap(media,MEDIA+"/GetProfiles","<m:GetProfiles xmlns:m='"+MEDIA+"'/>",auth);
            List<Profile> result=new ArrayList<>(); NodeList nodes=list.getElementsByTagNameNS("*","Profiles");
            for(int i=0;i<nodes.getLength();i++) {
                Element node=(Element)nodes.item(i); String token=node.getAttribute("token");
                Document stream=soap(media,MEDIA+"/GetStreamUri","<m:GetStreamUri xmlns:m='"+MEDIA+"' xmlns:t='http://www.onvif.org/ver10/schema'><m:StreamSetup><t:Stream>RTP-Unicast</t:Stream><t:Transport><t:Protocol>RTSP</t:Protocol></t:Transport></m:StreamSetup><m:ProfileToken>"+esc(token)+"</m:ProfileToken></m:GetStreamUri>",auth);
                Profile p=new Profile(); p.token=token; p.name=text(node,"Name"); p.uri=text(stream,"Uri");
                p.ptzUrl=node.getElementsByTagNameNS("*","PTZConfiguration").getLength()>0?ptz:"";
                p.deviceUrl=device;
                if(!p.uri.isEmpty()) result.add(p);
            }
            if(result.isEmpty()) throw new IOException("No profiles");
            return result;
        } catch(Exception e) { failure=e; }
        throw failure==null?new IOException("No ONVIF service"):failure;
    }
    static void move(Camera c,SecureCredentialsStore.Credentials auth,float x,float y) throws Exception {
        if(c.ptzUrl.isEmpty()||c.profileToken.isEmpty()) throw new IOException("PTZ not supported");
        soap(c.ptzUrl,PTZ+"/ContinuousMove","<p:ContinuousMove xmlns:p='"+PTZ+"' xmlns:t='http://www.onvif.org/ver10/schema'><p:ProfileToken>"+esc(c.profileToken)+"</p:ProfileToken><p:Velocity><t:PanTilt x='"+x+"' y='"+y+"'/></p:Velocity><p:Timeout>PT0.4S</p:Timeout></p:ContinuousMove>",auth);
    }
    static Document soap(String address,String action,String body,SecureCredentialsStore.Credentials auth) throws Exception {
        return soap(address,action,body,auth,null);
    }
    private static Document soap(String address,String action,String body,SecureCredentialsStore.Credentials auth,String authorization) throws Exception {
        CameraEditorActivity.validateHttp(address);
        byte[] nonce=new byte[20]; new SecureRandom().nextBytes(nonce);
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US); format.setTimeZone(TimeZone.getTimeZone("UTC")); String created=format.format(new Date());
        ByteArrayOutputStream digestInput=new ByteArrayOutputStream(); digestInput.write(nonce); digestInput.write(created.getBytes(StandardCharsets.UTF_8)); digestInput.write(auth.password.getBytes(StandardCharsets.UTF_8));
        String digest=android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(digestInput.toByteArray()),2);
        String security="<wsse:Security xmlns:wsse='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd' xmlns:wsu='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd'><wsse:UsernameToken><wsse:Username>"+esc(auth.username)+"</wsse:Username><wsse:Password Type='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest'>"+digest+"</wsse:Password><wsse:Nonce EncodingType='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary'>"+android.util.Base64.encodeToString(nonce,2)+"</wsse:Nonce><wsu:Created>"+created+"</wsu:Created></wsse:UsernameToken></wsse:Security>";
        String xml="<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Header>"+security+"</s:Header><s:Body>"+body+"</s:Body></s:Envelope>";
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(3500); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false); connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/soap+xml; charset=utf-8; action=\""+action+"\"");
        connection.setRequestProperty("Authorization",authorization==null?StreamEngine.basic(auth):authorization);
        try {
            try(OutputStream out=connection.getOutputStream()) { out.write(xml.getBytes(StandardCharsets.UTF_8)); }
            if(connection.getResponseCode()==401 && authorization==null) {
                String challenge=connection.getHeaderField("WWW-Authenticate");
                if(challenge!=null && challenge.toLowerCase(Locale.ROOT).startsWith("digest"))
                    return soap(address,action,body,auth,digest(challenge,address,auth));
            }
            if(connection.getResponseCode()!=200) throw new IOException("ONVIF HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while((n=in.read(buffer))!=-1) { if(out.size()+n>1024*1024) throw new IOException("Response too large"); out.write(buffer,0,n); }
                return parse(out.toString("UTF-8"));
            }
        } finally { connection.disconnect(); }
    }
    private static Document parse(String xml) throws Exception {
        if(xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) throw new IOException("DTD not permitted");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    private static String digest(String challenge,String address,SecureCredentialsStore.Credentials auth) throws Exception {
        Map<String,String> fields=new HashMap<>();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^, ]+))").matcher(challenge);
        while(m.find()) fields.put(m.group(1).toLowerCase(Locale.ROOT),m.group(2)!=null?m.group(2):m.group(3));
        String realm=fields.get("realm"),nonce=fields.get("nonce"),algorithm=fields.getOrDefault("algorithm","MD5");
        if(!algorithm.equalsIgnoreCase("MD5")) throw new IOException("Digest algorithm not supported");
        URI u=URI.create(address); String path=u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
        String cnonce=UUID.randomUUID().toString().replace("-","");
        String ha1=md5(auth.username+":"+realm+":"+auth.password),ha2=md5("POST:"+path);
        boolean qop=fields.getOrDefault("qop","").contains("auth");
        String response=md5(ha1+":"+nonce+":"+(qop?"00000001:"+cnonce+":auth:":"")+ha2);
        String header="Digest username=\""+quote(auth.username)+"\", realm=\""+quote(realm)+"\", nonce=\""+quote(nonce)+"\", uri=\""+quote(path)+"\", response=\""+response+"\", algorithm=MD5";
        if(qop) header+=", qop=auth, nc=00000001, cnonce=\""+cnonce+"\"";
        if(fields.containsKey("opaque")) header+=", opaque=\""+quote(fields.get("opaque"))+"\"";
        return header;
    }
    private static String md5(String value) throws Exception {
        byte[] bytes=MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder result=new StringBuilder();
        for(byte b:bytes) result.append(String.format(Locale.US,"%02x",b&255)); return result.toString();
    }
    private static String quote(String value) { return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"").replace("\r","").replace("\n",""); }
    static String text(Node node,String tag) { NodeList list=node instanceof Document?((Document)node).getElementsByTagNameNS("*",tag):((Element)node).getElementsByTagNameNS("*",tag); return list.getLength()==0?"":list.item(0).getTextContent(); }
    private static String address(Document doc,String tag) { NodeList list=doc.getElementsByTagNameNS("*",tag); return list.getLength()==0?"":text(list.item(0),"XAddr"); }
    private static String esc(String text) { return android.text.TextUtils.htmlEncode(text); }
}
