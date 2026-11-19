package android.view;
import android.content.Context;
public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public View(Context c) {}
    public ViewParent getParent() { return null; }
    public Context getContext() { return null; }
    public ViewGroup.LayoutParams getLayoutParams() { return null; }
    public void setLayoutParams(ViewGroup.LayoutParams p) {}
    public void setVisibility(int v) {}
    public int getVisibility() { return 0; }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean post(Runnable r) { return true; }
    public void setTag(Object o) {}
    public Object getTag() { return null; }
    public void setAlpha(float a) {}
}
