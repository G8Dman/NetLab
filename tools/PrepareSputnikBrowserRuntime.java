import java.nio.file.Path;
import de.keksuccino.rinku.*;
import de.keksuccino.rinku.binarydownload.RinkuDownloader;
/** Download/verify/extract only; never initializes CEF, Minecraft, a client, server or world. */
public class PrepareSputnikBrowserRuntime {
    public static void main(String[] args)throws Exception{
        var downloader=new RinkuDownloader(RinkuDownloader.OFFICIAL_MIRROR,"2eb4ca2648bda91d1dfed81e9a37ba92e757aff9",OSPlatform.LINUX_AMD64,RinkuDownloader.DownloadPolicy.defaults(),Path.of(args[0]),null,null);
        System.out.println("Preparing browser files from "+downloader.getJavaCefDownloadUrl());
        var result=downloader.installOrUpdate(false);System.out.println("Verified browser files: "+result.installationDirectory()+"; downloaded="+result.downloaded());
    }
}
