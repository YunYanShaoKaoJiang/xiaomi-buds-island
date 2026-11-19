package android.media;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
public class MediaMetadataRetriever {
    public static final int METADATA_KEY_DURATION = 9;
    public MediaMetadataRetriever() {}
    public void setDataSource(Context c, Uri uri) {}
    public void setDataSource(String path) {}
    public Bitmap getFrameAtIndex(int index) { return null; }
    public Bitmap getFrameAtTime(long timeUs, int option) { return null; }
    public String extractMetadata(int key) { return null; }
    public void release() {}
}
