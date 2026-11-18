from pathlib import Path
import os, subprocess
root=Path('build/update-channel-validation')
cache=Path.home()/'.gradle/caches'
gson=next(p for p in cache.rglob('gson-2.2.4.jar') if 'modules-2' in str(p))
forge=next(cache.glob('minecraft/net/minecraftforge/forge/1.7.10*/unpacked/binaries.jar'))
guava=next(p for p in cache.rglob('guava-17.0.jar') if 'modules-2' in str(p))
sources={
'com/science/gtnl/ScienceNotLeisure.java': 'package com.science.gtnl; public class ScienceNotLeisure { public static final Logger LOG=new Logger(); public static class Logger { public void info(String s,Object... o){} } }',
'com/science/gtnl/Tags.java':'package com.science.gtnl; public class Tags { public static final String VERSION="0.2.7"; }',
'Harness.java':r'''
import java.io.*; import java.net.*; import java.nio.file.*; import java.nio.charset.StandardCharsets; import java.util.*; import java.util.concurrent.*; import java.lang.reflect.*;
import com.science.gtnl.common.update.UpdateChecker;
public class Harness {
 static String repo="GTNotLeisure/GT-Not-Leisure", branch="dev-290", base=repeat('a'), newer=repeat('b'), key="fde37b9871fd";
 static Map<String,String> responses=new HashMap<>(); static int requests; static int status=200; static CountDownLatch gate;
 static String repeat(char c){char[] a=new char[40];Arrays.fill(a,c);return new String(a);}
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 static String asset(String name){return "{\"name\":\""+name+"\",\"state\":\"uploaded\",\"browser_download_url\":\"https://github.com/"+repo+"/releases/download/dev-build/"+name+"\"}";}
 static void identity(String channel,String version) throws Exception {
  Field f=UpdateChecker.class.getDeclaredField("check");f.setAccessible(true);f.set(null,null);
  Properties p=new Properties(); p.setProperty("repository",repo);p.setProperty("branch",branch);p.setProperty("commit",base);p.setProperty("channel",channel);p.setProperty("version",version);p.setProperty("releaseRange","[0.2.7,)");
  try(OutputStream out=Files.newOutputStream(Paths.get(argsRoot,"gtnl-build.properties"))){p.store(out,"");}
  requests=0; status=200;responses.clear();
 }
 static String argsRoot;
 static UpdateChecker.Result run() throws Exception {return UpdateChecker.start().get(3,TimeUnit.SECONDS);}
 static void dev(String manifestBranch,String commit,String artifact,String relation) {
  String manifest="gtnl-update-"+key+".json";
  responses.put("/repos/"+repo+"/releases/tags/dev-build","{\"draft\":false,\"assets\":["+asset(manifest)+","+asset("install.jar")+"]}");
  responses.put("/"+repo+"/releases/download/dev-build/"+manifest,"{\"schema\":1,\"channel\":\"development\",\"repository\":\""+repo+"\",\"branch\":\""+manifestBranch+"\",\"commit\":\""+commit+"\",\"version\":\"0.2.7-dev.dev-290.123+bbbbbbbb\",\"artifact\":\""+artifact+"\"}");
  responses.put("/repos/"+repo+"/compare/"+base+"..."+commit,"{\"status\":\""+relation+"\",\"ahead_by\":2}");
 }
 public static void main(String[] args)throws Exception{
  argsRoot=args[0];
  URL.setURLStreamHandlerFactory(protocol -> !protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL url){
   ok(!Thread.currentThread().getName().equals("main"),"network on main thread"); requests++;
   return new HttpURLConnection(url){public void connect(){} public void disconnect(){}public boolean usingProxy(){return false;}
    public int getResponseCode()throws IOException{if(gate!=null)try{gate.await();}catch(Exception e){throw new IOException(e);}return status;}
    public InputStream getInputStream(){String data=responses.get(url.getPath());if(data==null)throw new AssertionError("Unexpected URL: "+url);return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));}
   };}});
  identity("stable","0.2.7");
  responses.put("/repos/"+repo+"/releases","[{\"draft\":false,\"prerelease\":false,\"tag_name\":\"0.2.8\",\"assets\":["+asset("sciencenotleisure-0.2.8.jar")+"]},{\"draft\":false,\"prerelease\":false,\"tag_name\":\"dev-build\",\"assets\":[]},{\"draft\":false,\"prerelease\":false,\"tag_name\":\"0.3.0-rc1\",\"assets\":[]}]");
  gate=new CountDownLatch(1); CompletableFuture<UpdateChecker.Result> future=UpdateChecker.start();ok(future.getNow(null)==null,"blocking start");ok(future==UpdateChecker.start(),"duplicate task");gate.countDown();UpdateChecker.Result r=future.get(3,TimeUnit.SECONDS);gate=null;ok(r.hasUpdate()&&r.version.equals("0.2.8"),"stable candidate");
  identity("development","0.2.7-dev.dev-290.122+aaaaaaaa");dev(branch,newer,"install.jar","ahead");r=run();ok(r.hasUpdate()&&r.downloadUrl().endsWith("install.jar"),"ahead manifest");ok(UpdateChecker.currentVersion().contains("122"),"display identity");
  for(String relation:new String[]{"behind","diverged","identical"}){identity("development","dev");dev(branch,newer,"install.jar",relation);ok(!run().hasUpdate(),relation);}
  identity("development","dev");dev(branch,base,"install.jar","identical");ok(!run().hasUpdate()&&requests==2,"same commit");
  identity("development","dev");dev("other-branch",newer,"install.jar","ahead");ok(run().failure.contains("mismatch"),"branch mismatch");
  identity("development","dev");dev(branch,newer,"missing.jar","ahead");ok(!run().hasUpdate(),"no downloadable asset");
  identity("development","dev");String name="sciencenotleisure-dev-290-"+key+"-bbbbbbbbbbbb.jar";
  responses.put("/repos/"+repo+"/releases/tags/dev-build","{\"draft\":false,\"assets\":["+asset(name)+","+asset("sciencenotleisure-dev-290-dev-"+key+"-bbbbbbbbbbbb.jar")+"]}");responses.put("/repos/"+repo+"/commits/bbbbbbbbbbbb","{\"sha\":\""+newer+"\"}");responses.put("/repos/"+repo+"/compare/"+base+"..."+newer,"{\"status\":\"ahead\",\"ahead_by\":1}");ok(run().hasUpdate(),"legacy branch asset");
  identity("local","dev");ok(!run().hasUpdate()&&requests==0,"local skip");
  identity("development","dev");status=403;r=run();ok(r.failure.contains("403"),"HTTP error");int count=requests;run();ok(requests==count,"retry");
  System.out.println("PASS: stable/dev separation, branch identity, full-SHA ancestry, same/behind/diverged, missing artifacts, legacy assets, local skip, HTTP failure, nonblocking worker, single task");
 }
}'''}
for name,text in sources.items():
 p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
classes=root/'classes';classes.mkdir(exist_ok=True)
cp=os.pathsep.join(map(str,[gson,forge,guava]))
files=[str(root/n) for n in sources]+['src/main/java/com/science/gtnl/common/update/BuildIdentity.java','src/main/java/com/science/gtnl/common/update/UpdateChecker.java']
subprocess.run(['javac','--release','8','-proc:none','-cp',cp,'-d',str(classes)]+files,check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+cp,'Harness',str(classes)],check=True)