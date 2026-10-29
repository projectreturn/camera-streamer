package de.local.tapoviewer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Explicit, ten-minute LAN-only transfer of already encrypted backups. Never handles passwords. */
final class BackupTransfer implements AutoCloseable {
    interface Receiver {void received(byte[] encrypted);}
    private final ServerSocket server;
    private volatile Socket client;
    private volatile boolean closed;
    private final String path;
    private final byte[] download;
    private final Receiver receiver;
    private final long deadline=System.nanoTime()+600_000_000_000L;
    BackupTransfer(String address,byte[] download,Receiver receiver)throws IOException{
        InetAddress bind=InetAddress.getByName(address);if(!bind.isSiteLocalAddress()&&!bind.isLoopbackAddress())throw new IOException("Kein lokales Netzwerk");
        server=new ServerSocket(0,4,bind);server.setSoTimeout(1000);this.download=download;this.receiver=receiver;
        byte[] token=new byte[8];new SecureRandom().nextBytes(token);StringBuilder secret=new StringBuilder("/");for(byte b:token)secret.append(String.format(Locale.ROOT,"%02x",b&255));path=secret.toString();
        Thread thread=new Thread(this::serve,"backup-transfer");thread.setDaemon(true);thread.start();
    }
    String url(){return "http://"+server.getInetAddress().getHostAddress()+":"+server.getLocalPort()+path;}
    private void serve(){try{while(!closed&&System.nanoTime()<deadline){try{client=server.accept();client.setSoTimeout(10000);handle(client);}catch(SocketTimeoutException ignored){}catch(IOException ignored){}finally{Socket current=client;client=null;if(current!=null)try{current.close();}catch(IOException ignored){}}}}finally{close();}}
    private void handle(Socket socket)throws IOException{
        InputStream in=new BufferedInputStream(socket.getInputStream());OutputStream out=socket.getOutputStream();String first=line(in);String[] request=first.split(" ");if(request.length!=3){respond(out,400,"text/plain","Ungültige Anfrage".getBytes(StandardCharsets.UTF_8));return;}
        Map<String,String> headers=new HashMap<>();int size=first.length();String header;while(!(header=line(in)).isEmpty()){size+=header.length();if(size>8192)throw new IOException();int colon=header.indexOf(':');if(colon>0)headers.put(header.substring(0,colon).trim().toLowerCase(Locale.ROOT),header.substring(colon+1).trim());}
        if(!request[1].equals(path)&&!request[1].equals(path+"/download")){respond(out,404,"text/plain",new byte[0]);return;}
        if(request[0].equals("GET")&&request[1].equals(path+"/download")&&download!=null){respond(out,200,"application/x-camera-streamer-backup",download);return;}
        if(request[0].equals("GET")&&request[1].equals(path)){respond(out,200,"text/html; charset=utf-8",page().getBytes(StandardCharsets.UTF_8));return;}
        if(!request[0].equals("POST")||!request[1].equals(path)||download!=null){respond(out,405,"text/plain",new byte[0]);return;}
        int length;try{length=Integer.parseInt(headers.getOrDefault("content-length","0"));}catch(Exception e){length=0;}if(length<48||length>4*1024*1024){respond(out,413,"text/plain","Datei ist zu groß oder ungültig".getBytes(StandardCharsets.UTF_8));return;}
        byte[] bytes=new byte[length];int read=0,n;while(read<length&&(n=in.read(bytes,read,length-read))!=-1)read+=n;
        if(read!=length||bytes[0]!='C'||bytes[1]!='S'||bytes[2]!='B'||bytes[3]!='1'){respond(out,400,"text/plain","Keine camera-streamer-Sicherung".getBytes(StandardCharsets.UTF_8));return;}
        respond(out,200,"text/plain; charset=utf-8","Datei übertragen. Wiederherstellung bitte am Fernseher bestätigen.".getBytes(StandardCharsets.UTF_8));receiver.received(bytes);close();
    }
    private static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')return out.toString("US-ASCII").replace("\r","");if(out.size()>=8192)throw new IOException();out.write(b);}throw new EOFException();}
    private static void respond(OutputStream out,int status,String type,byte[] bytes)throws IOException{
        String headers="HTTP/1.1 "+status+" "+(status==200?"OK":"Error")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'\r\n"+(type.equals("application/x-camera-streamer-backup")?"Content-Disposition: attachment; filename=\"camera-streamer-backup.csb\"\r\n":"")+"\r\n";out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
    private String page(){String content=download!=null?"<a href='"+path+"/download' download='camera-streamer-backup.csb'>Verschlüsselte Sicherung herunterladen</a>":"<input id='file' type='file' accept='.csb'><button id='send'>Zum Fernseher übertragen</button><p id='status'></p><script>document.getElementById('send').onclick=async()=>{const f=document.getElementById('file').files[0];if(!f)return;const s=document.getElementById('status');if(f.size>4194304){s.textContent='Datei zu groß';return;}s.textContent='Übertragung läuft …';try{const r=await fetch(location.pathname,{method:'POST',headers:{'Content-Type':'application/octet-stream'},body:f});s.textContent=await r.text();}catch(e){s.textContent='Übertragung fehlgeschlagen. TV-Dialog und Netzwerk prüfen.';}};</script>";return "<!doctype html><html lang='de'><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>camera streamer · Sicherung</title><style>body{font:18px system-ui;background:#08172b;color:white;max-width:660px;margin:50px auto;padding:24px}a,button{display:block;background:#1bc2e4;color:#05101b;padding:18px;margin:22px 0;border:0;border-radius:12px;text-decoration:none;font:inherit}input{max-width:100%}p{line-height:1.5;color:#b2c6df}</style><h1>camera streamer</h1><h2>Sicherungsübertragung</h2><p>Nur im Heimnetz und während der TV-Dialog geöffnet ist (maximal zehn Minuten). Die Datei bleibt passwortverschlüsselt. Das Sicherungspasswort wird nur am Fernseher eingegeben.</p>"+content+"</html>";}
    @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}Socket current=client;if(current!=null)try{current.close();}catch(IOException ignored){}}
}
