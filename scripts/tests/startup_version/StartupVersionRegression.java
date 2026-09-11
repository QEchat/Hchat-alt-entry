import android.content.Context;
import android.content.pm.*;
import com.tencent.mm.boot.BuildConfig;
import h.Hchat.hooks.api.model.WeChatVersionInfo;
import h.Hchat.hooks.api.runtime.WeChatVersionApi;
import java.io.File;
import java.nio.file.*;

public class StartupVersionRegression {
 static final class Host extends Context {
  final File files;
  int fileReads;
  Host(Path root) throws Exception { files=Files.createDirectories(root.resolve("files")).toFile(); }
  public String getPackageName() { return "com.tencent.mm"; }
  public PackageManager getPackageManager() { return new PackageManager(); }
  public ApplicationInfo getApplicationInfo() { return new ApplicationInfo(); }
  public File getFilesDir() { fileReads++; return files; }
 }
 static final class Loader extends ClassLoader {
  String metadata;
  boolean hideBuildConfig;
  Loader(String metadata) { super(StartupVersionRegression.class.getClassLoader()); this.metadata=metadata; }
  public String toString() { return metadata; }
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
   if (hideBuildConfig && name.equals("com.tencent.mm.boot.BuildConfig")) throw new ClassNotFoundException(name);
   return super.loadClass(name, resolve);
  }
 }
 static int checks;
 static void eq(Object expected, Object actual, String label) {
  checks++;
  if (!expected.equals(actual)) throw new AssertionError(label+": expected="+expected+", actual="+actual);
 }
 static void write(Path file, String value) throws Exception { Files.writeString(file, value); }
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory(Path.of(args[0]), "runtime-");
  Host host=new Host(root);
  Path tinker=Files.createDirectories(root.resolve("tinker"));
  Path meta=tinker.resolve("patch.properties");
  Path patch=Files.createDirectories(tinker.resolve("patch-one"));
  write(meta, "patch.client.ver=300\nclient.ver=400\nNEW_TINKER_ID=file-new\nTINKER_ID=file-old\n");
  Loader loader=new Loader("clientVersion=200 TINKER_ID=loader-id intent_patch_new_version=loader-patch");
  BuildConfig.CLIENT_VERSION_ARM64="100";
  BuildConfig.CLIENT_VERSION="101";
  WeChatVersionInfo info=WeChatVersionApi.build(host,loader);
  eq("100",info.clientVersion,"BuildConfig has highest priority");
  eq("loader-id",info.tinkerId,"loader tinker priority");
  eq("loader-patch",info.patchId,"loader patch priority");
  eq(0,host.fileReads,"complete loader metadata does not access Tinker directory");
  BuildConfig.CLIENT_VERSION_ARM64="";
  eq("101",WeChatVersionApi.build(host,loader).clientVersion,"second BuildConfig field");
  BuildConfig.CLIENT_VERSION="";
  BuildConfig.CLIENT_VERSION_INT="102";
  eq("102",WeChatVersionApi.build(host,loader).clientVersion,"third BuildConfig field");
  BuildConfig.CLIENT_VERSION_INT="";
  BuildConfig.CLIENTVERSION="103";
  eq("103",WeChatVersionApi.build(host,loader).clientVersion,"fourth BuildConfig field");
  BuildConfig.CLIENTVERSION="";
  eq("200",WeChatVersionApi.build(host,loader).clientVersion,"loader beats files");
  eq(0,host.fileReads,"all complete metadata builds skip file directory");
  loader.hideBuildConfig=true;
  eq("200",WeChatVersionApi.build(host,loader).clientVersion,"missing BuildConfig fallback");
  loader.metadata="plain-loader";
  info=WeChatVersionApi.build(host,loader);
  eq("300",info.clientVersion,"patch.client.ver beats client.ver");
  eq("file-new",info.tinkerId,"NEW_TINKER_ID beats TINKER_ID");
  eq("patch-one",info.patchId,"patch directory fallback");
  String before=info.cacheKey;
  write(meta,"patch.client.ver=301\nclient.ver=401\nNEW_TINKER_ID=changed\nTINKER_ID=old\n");
  info=WeChatVersionApi.build(host,loader);
  eq("301",info.clientVersion,"next build observes metadata update");
  eq("changed",info.tinkerId,"next build observes Tinker update");
  eq(false,before.equals(info.cacheKey),"cache key immediately invalidates");
  write(meta,"client.ver=402\nTINKER_ID=only-old\n");
  info=WeChatVersionApi.build(host,loader);
  eq("402",info.clientVersion,"legacy client key fallback");
  eq("only-old",info.tinkerId,"legacy Tinker key fallback");
  WeChatVersionApi api=new WeChatVersionApi(host,loader,null);
  eq("402",api.current().clientVersion,"instance initial metadata");
  write(meta,"client.ver=403\nTINKER_ID=latest\n");
  eq("403",api.current().clientVersion,"instance immediately refreshes metadata");
  write(meta,"# patch.client.ver=999\n! patch.client.ver=998\nbackup.patch.client.ver=997\npatch.client.ver.old=996\nnote=patch.client.ver=995\nclient.ver=404\n# NEW_TINKER_ID=comment\n! NEW_TINKER_ID=comment2\nBACKUP_NEW_TINKER_ID=wrong\nNEW_TINKER_ID.old=wrong2\nTINKER_ID=real-old\n");
  info=api.current();
  eq("404",info.clientVersion,"comments and similar keys cannot supply client version");
  eq("real-old",info.tinkerId,"comments and similar keys cannot supply Tinker ID");
  write(meta," patch.client.ver =   \n patch.client.ver : 405 \n NEW_TINKER_ID =   \n NEW_TINKER_ID : new=value:tail \n");
  before=info.cacheKey;
  info=api.current();
  eq("405",info.clientVersion,"blank values are skipped and colon separators accepted");
  eq("new=value:tail",info.tinkerId,"only the first separator splits a metadata entry");
  eq(false,before.equals(info.cacheKey),"corrected metadata invalidates previous runtime key");
  write(meta,"client.ver=406\nTINKER_ID=final\n");
  eq("406",api.current().clientVersion,"legacy fallback remains live after metadata replacement");
  loader.metadata="clientVersion=900 TINKER_ID=runtime-new intent_patch_old_version=runtime-patch";
  int reads=host.fileReads;
  eq("900",api.current().clientVersion,"loader mutation immediately visible");
  eq(reads,host.fileReads,"runtime loader fallback skips disk");
  System.out.println("StartupVersionRegression: "+checks+" checks passed");
 }
}
