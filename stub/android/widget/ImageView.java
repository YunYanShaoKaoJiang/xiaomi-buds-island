package android.widget;
import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
public class ImageView extends View {
    public enum ScaleType { FIT_XY, FIT_CENTER, CENTER_CROP, CENTER }
    public ImageView(Context c) { super(c); }
    public void setImageDrawable(Drawable d) {}
    public Drawable getDrawable() { return null; }
    public void setScaleType(ScaleType t) {}
    public void setColorFilter(ColorFilter cf) {}
}
