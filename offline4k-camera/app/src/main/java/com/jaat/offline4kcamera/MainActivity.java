package com.jaat.offline4kcamera;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import android.util.Size;
import java.io.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    TextureView view;
    CameraDevice cam;
    CameraCaptureSession session;
    ImageReader reader;
    String camId;
    Size size;
    boolean front = false, busy = false;
    int mode = 0;
    HandlerThread ht;
    Handler h;
    TextView info;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ht = new HandlerThread("camera"); ht.start(); h = new Handler(ht.getLooper());
        ui();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 10);
        else view.post(this::open);
    }

    void ui() {
        FrameLayout root = new FrameLayout(this);
        view = new TextureView(this); root.addView(view);
        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){open();}
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s){close();return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture s){}
        });
        LinearLayout top = new LinearLayout(this); top.setPadding(12,18,12,0);
        Button flip=b("↻"), gal=b("▣"); info=t("PHOTO • ON-DEVICE",15);
        top.addView(info,new LinearLayout.LayoutParams(0,60,1)); top.addView(gal,new LinearLayout.LayoutParams(70,60)); top.addView(flip,new LinearLayout.LayoutParams(70,60));
        root.addView(top,lp(-1,80,Gravity.TOP));
        LinearLayout modes=new LinearLayout(this); modes.setGravity(Gravity.CENTER);
        Button n=b("PHOTO"), k2=b("2K"), k4=b("4K"); modes.addView(n); modes.addView(k2); modes.addView(k4); root.addView(modes,lp(-1,65,Gravity.BOTTOM));
        Button shot=b("●"); shot.setTextSize(34); FrameLayout.LayoutParams sp=lp(100,100,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL); sp.bottomMargin=55; root.addView(shot,sp);
        n.setOnClickListener(v->{mode=0;info.setText("PHOTO • ON-DEVICE");});
        k2.setOnClickListener(v->{mode=2;info.setText("2K • ON-DEVICE");});
        k4.setOnClickListener(v->{mode=4;info.setText("4K • ON-DEVICE");});
        shot.setOnClickListener(v->capture());
        flip.setOnClickListener(v->{front=!front;close();view.postDelayed(this::open,200);});
        gal.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_VIEW);i.setType("image/*");startActivity(i);});
        setContentView(root);
    }

    FrameLayout.LayoutParams lp(int w,int h,int g){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.gravity=g;return p;}
    Button b(String s){Button x=new Button(this);x.setText(s);x.setTextColor(Color.WHITE);x.setBackgroundColor(Color.TRANSPARENT);return x;}
    TextView t(String s,int z){TextView x=new TextView(this);x.setText(s);x.setTextColor(Color.WHITE);x.setTextSize(z);x.setGravity(Gravity.CENTER_VERTICAL);return x;}

    void open(){
        try {
            CameraManager m=(CameraManager)getSystemService(CAMERA_SERVICE);
            for(String id:m.getCameraIdList()){
                CameraCharacteristics c=m.getCameraCharacteristics(id); Integer f=c.get(CameraCharacteristics.LENS_FACING);
                if((!front&&f!=null&&f==CameraCharacteristics.LENS_FACING_BACK)||(front&&f!=null&&f==CameraCharacteristics.LENS_FACING_FRONT)){camId=id;break;}
            }
            if(camId==null) throw new IllegalStateException("No camera");
            CameraCharacteristics c=m.getCameraCharacteristics(camId);
            android.hardware.camera2.params.StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] ss=map.getOutputSizes(ImageFormat.JPEG);
            size=ss[0]; for(Size s:ss) if((long)s.getWidth()*s.getHeight()>(long)size.getWidth()*size.getHeight()) size=s;
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.JPEG,2); reader.setOnImageAvailableListener(this::image,h);
            if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
            m.openCamera(camId,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){cam=c;preview();}public void onDisconnected(CameraDevice c){c.close();cam=null;}public void onError(CameraDevice c,int e){c.close();cam=null;}},h);
        } catch(Exception e) { info.setText("Camera error"); }
    }

    void preview(){
        try{
            SurfaceTexture st=view.getSurfaceTexture(); st.setDefaultBufferSize(size.getWidth(),size.getHeight()); Surface s=new Surface(st);
            CaptureRequest.Builder q=cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); q.addTarget(s);
            cam.createCaptureSession(Arrays.asList(s,reader.getSurface()),new CameraCaptureSession.StateCallback(){
                public void onConfigured(CameraCaptureSession x){session=x;try{q.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);x.setRepeatingRequest(q.build(),null,h);}catch(Exception e){}}
                public void onConfigureFailed(CameraCaptureSession x){info.post(()->info.setText("Preview unavailable"));}
            },h);
        }catch(Exception e){info.setText("Preview error");}
    }

    void capture(){
        if(session==null||cam==null||busy)return; busy=true; info.setText("Capturing…");
        try{CaptureRequest.Builder q=cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);q.addTarget(reader.getSurface());q.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);session.capture(q.build(),null,h);}
        catch(Exception e){busy=false;info.setText("Capture failed");}
    }

    void image(ImageReader r){
        Image im=null;
        try{
            im=r.acquireLatestImage(); if(im==null)return; ByteBuffer bb=im.getPlanes()[0].getBuffer(); byte[] d=new byte[bb.remaining()];bb.get(d);
            Bitmap b=BitmapFactory.decodeByteArray(d,0,d.length); if(b==null)throw new IOException("Invalid JPEG");
            if(front){Matrix m=new Matrix();m.postRotate(270);b=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);}
            int savedMode=mode; if(savedMode>0)b=up(b,savedMode); save(b);
            info.post(()->info.setText(savedMode>0?savedMode+"K SAVED • ON-DEVICE":"PHOTO SAVED"));
        }catch(Exception e){info.post(()->info.setText("Processing failed"));}
        finally{if(im!=null)im.close();busy=false;}
    }

    Bitmap up(Bitmap b,int k){
        int w=k==4?3840:2560,h=k==4?2160:1440; float sr=(float)b.getWidth()/b.getHeight(),dr=(float)w/h; int cw=b.getWidth(),ch=b.getHeight();
        if(sr>dr)cw=Math.round(ch*dr);else ch=Math.round(cw/dr);
        Bitmap c=Bitmap.createBitmap(b,(b.getWidth()-cw)/2,(b.getHeight()-ch)/2,cw,ch); Bitmap o=Bitmap.createScaledBitmap(c,w,h,true);
        if(c!=b)c.recycle(); if(b!=o)b.recycle(); return o;
    }

    void save(Bitmap b)throws Exception{
        String n="OfflineCamera_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".jpg"; ContentValues v=new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME,n);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Offline4KCamera");v.put(MediaStore.Images.Media.IS_PENDING,1);}
        Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("MediaStore insert failed");
        try(OutputStream o=getContentResolver().openOutputStream(u)){if(o==null)throw new IOException("Output stream unavailable");b.compress(Bitmap.CompressFormat.JPEG,96,o);}
        if(Build.VERSION.SDK_INT>=29){v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,v,null,null);} b.recycle();
    }

    void close(){try{if(session!=null)session.close();if(cam!=null)cam.close();if(reader!=null)reader.close();}catch(Exception e){}session=null;cam=null;reader=null;}
    @Override protected void onDestroy(){close();ht.quitSafely();super.onDestroy();}
}
