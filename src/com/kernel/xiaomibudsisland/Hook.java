package com.kernel.xiaomibudsisland;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "IslandAlpha ";
    private static final String TARGET = "miui.systemui.widget.TextureVideoView";
    private static final int CACHE_LIMIT = 6;

    private static final Map<View, ImageView> OVERLAYS = new WeakHashMap<View, ImageView>();
    private static final LinkedHashMap<String, ArrayList<Bitmap>> CACHE =
            new LinkedHashMap<String, ArrayList<Bitmap>>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ArrayList<Bitmap>> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp == null || !"com.android.systemui".equals(lp.packageName)) {
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(View.class, "onVisibilityAggregated", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final View v = (View) param.thisObject;
                    if (v == null) return;
                    try {
                        if (!TARGET.equals(v.getClass().getName())) return;
                        Object vis = param.args[0];
                        final boolean visible = vis instanceof Boolean && (Boolean) vis;
                        v.post(new Runnable() {
                            public void run() {
                                try {
                                    if (visible) {
                                        handle(v);
                                    } else {
                                        hide(v);
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "post failed: " + t);
                                }
                            }
                        });
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "cb failed: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "hooked View.onVisibilityAggregated (v2)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook failed: " + t);
        }
    }

    private static boolean isTarget(Uri uri) {
        String s = uri.toString();
        return s.contains("xiaomi.bluetooth") || s.contains("ldi_") || s.contains("tws_earphone");
    }

    private static void hide(View v) {
        ImageView iv = OVERLAYS.remove(v);
        if (iv == null) return;
        Drawable d = iv.getDrawable();
        if (d instanceof AnimationDrawable) {
            try {
                ((AnimationDrawable) d).stop();
            } catch (Throwable ignored) {
            }
        }
        try {
            iv.setImageDrawable(null);
        } catch (Throwable ignored) {
        }
        ViewParent p = iv.getParent();
        if (p instanceof ViewGroup) {
            try {
                ((ViewGroup) p).removeView(iv);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void handle(View v) {
        Object u = XposedHelpers.getObjectField(v, "mUri");
        if (!(u instanceof Uri)) return;
        Uri uri = (Uri) u;
        if (!isTarget(uri)) return;
        ViewParent p = v.getParent();
        if (!(p instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) p;
        String key = uri.toString();

        ImageView iv = OVERLAYS.get(v);
        if (iv != null && key.equals(iv.getTag()) && iv.getParent() == parent) {
            iv.setVisibility(View.VISIBLE);
            Drawable d = iv.getDrawable();
            if (d instanceof AnimationDrawable) {
                try {
                    if (!((AnimationDrawable) d).isRunning()) ((AnimationDrawable) d).start();
                } catch (Throwable ignored) {
                }
            }
            pin(v);
            return;
        }

        ArrayList<Bitmap> frames = loadFrames(v.getContext(), uri, key);
        if (frames == null || frames.isEmpty()) return;

        long dur = 1000L;
        try {
            MediaMetadataRetriever mm = new MediaMetadataRetriever();
            mm.setDataSource(v.getContext(), uri);
            String s = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (s != null) dur = Long.parseLong(s);
            mm.release();
        } catch (Throwable ignored) {
        }
        int delay = (int) Math.max(20L, dur / frames.size());

        AnimationDrawable ad = new AnimationDrawable();
        for (int i = 0; i < frames.size(); i++) {
            ad.addFrame(new BitmapDrawable(v.getContext().getResources(), frames.get(i)), delay);
        }
        ad.setOneShot(false);
        float[] mtx = new float[]{
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f
        };
        ad.setColorFilter(new ColorMatrixColorFilter(mtx));

        if (iv != null) {
            iv.setImageDrawable(null);
            ViewParent op = iv.getParent();
            if (op instanceof ViewGroup) ((ViewGroup) op).removeView(iv);
        }
        iv = new ImageView(v.getContext());
        iv.setTag(key);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        iv.setImageDrawable(ad);
        try {
            parent.addView(iv, parent.indexOfChild(v) + 1, cloneLp(v));
        } catch (Throwable t) {
            XposedBridge.log(TAG + "add failed: " + t);
            parent.addView(iv, new ViewGroup.LayoutParams(v.getWidth(), v.getHeight()));
        }
        OVERLAYS.put(v, iv);
        pin(v);
        final ImageView fiv = iv;
        iv.post(new Runnable() {
            public void run() {
                try {
                    ad.start();
                } catch (Throwable ignored) {
                }
            }
        });
        XposedBridge.log(TAG + "overlay " + frames.size() + " frames, " + delay + "ms, " + uri);
    }

    private static String cls(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }

    private static ViewGroup.LayoutParams cloneLp(View v) {
        ViewGroup.LayoutParams src = v.getLayoutParams();
        if (src == null) return new ViewGroup.LayoutParams(v.getWidth(), v.getHeight());
        try {
            java.lang.reflect.Constructor<?> c = src.getClass().getConstructor(int.class, int.class);
            return (ViewGroup.LayoutParams) c.newInstance(src.width, src.height);
        } catch (Throwable t) {
            return new ViewGroup.LayoutParams(src.width, src.height);
        }
    }

    private static void pin(View v) {
        try {
            v.setAlpha(0f);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method p = v.getClass().getMethod("pause");
            p.invoke(v);
        } catch (Throwable ignored) {
        }
    }

    private static ArrayList<Bitmap> loadFrames(Context ctx, Uri uri, String key) {
        synchronized (CACHE) {
            ArrayList<Bitmap> hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        ArrayList<Bitmap> frames = new ArrayList<Bitmap>();
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(ctx, uri);
            for (int i = 0; i < 90; i++) {
                Bitmap b;
                try {
                    b = mmr.getFrameAtIndex(i);
                } catch (Throwable t) {
                    break;
                }
                if (b == null) break;
                frames.add(b);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "decode failed: " + t);
            return null;
        } finally {
            try {
                mmr.release();
            } catch (Throwable ignored) {
            }
        }
        if (!frames.isEmpty()) {
            synchronized (CACHE) {
                CACHE.put(key, frames);
            }
        }
        return frames;
    }
}
