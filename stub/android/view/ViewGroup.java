package android.view;
import android.content.Context;
public class ViewGroup extends View implements ViewParent {
    public ViewGroup(Context c) { super(c); }
    public static class LayoutParams {
        public int width;
        public int height;
        public LayoutParams(int w, int h) { this.width = w; this.height = h; }
    }
    public int getChildCount() { return 0; }
    public View getChildAt(int i) { return null; }
    public void addView(View v) {}
    public void addView(View v, int index) {}
    public void addView(View v, ViewGroup.LayoutParams p) {}
    public void addView(View v, int index, ViewGroup.LayoutParams p) {}
    public void removeView(View v) {}
    public int indexOfChild(View v) { return -1; }
}
