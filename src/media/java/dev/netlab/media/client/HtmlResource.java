package dev.netlab.media.client;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.misc.*;
import org.cef.network.*;
import java.nio.charset.StandardCharsets;
/** Local player document has an HTTPS origin; only remote media bytes enter the network tunnel. */
public final class HtmlResource extends CefResourceHandlerAdapter {
    private final byte[] body;private int offset;
    public HtmlResource(String html){body=html.getBytes(StandardCharsets.UTF_8);}
    @Override public boolean processRequest(CefRequest q,CefCallback cb){cb.Continue();return true;}
    @Override public void getResponseHeaders(CefResponse response,IntRef length,StringRef redirect){response.setStatus(200);response.setMimeType("text/html");length.set(body.length);}
    @Override public boolean readResponse(byte[] data,int max,IntRef read,CefCallback cb){int n=Math.min(max,body.length-offset);if(n<=0){read.set(0);return false;}System.arraycopy(body,offset,data,0,n);offset+=n;read.set(n);return true;}
}
