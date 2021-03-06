package com.cgfay.caincamera.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.cgfay.caincamera.R;
import com.cgfay.caincamera.adapter.EffectFilterAdapter;
import com.cgfay.caincamera.core.AspectRatioType;
import com.cgfay.caincamera.core.ColorFilterManager;
import com.cgfay.caincamera.core.DrawerManager;
import com.cgfay.caincamera.core.ParamsManager;
import com.cgfay.caincamera.core.RecorderManager;
import com.cgfay.caincamera.facetracker.FaceTrackManager;
import com.cgfay.caincamera.multimedia.MediaEncoder;
import com.cgfay.caincamera.multimedia.MediaVideoEncoder;
import com.cgfay.caincamera.type.GalleryType;
import com.cgfay.caincamera.utils.CameraUtils;
import com.cgfay.caincamera.utils.FileUtils;
import com.cgfay.caincamera.utils.PermissionUtils;
import com.cgfay.caincamera.utils.TextureRotationUtils;
import com.cgfay.caincamera.view.AspectFrameLayout;
import com.cgfay.caincamera.view.AsyncRecyclerview;
import com.cgfay.caincamera.view.CameraSurfaceView;
import com.cgfay.caincamera.view.HorizontalIndicatorView;
import com.cgfay.caincamera.view.PictureVideoActionButton;

import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener,
        CameraSurfaceView.OnClickListener, CameraSurfaceView.OnTouchScroller,
        HorizontalIndicatorView.IndicatorListener, PictureVideoActionButton.ActionListener {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CAMERA = 0x01;
    private static final int REQUEST_STORAGE_READ = 0x02;
    private static final int REQUEST_STORAGE_WRITE = 0x03;
    private static final int REQUEST_RECORD = 0x04;
    private static final int REQUEST_LOCATION = 0x05;

    private static final int FocusSize = 100;
    // 权限使能标志
    private boolean mCameraEnable = false;
    private boolean mStorageWriteEnable = false;
    private boolean mRecordSoundEnable = false;
    private boolean mLocationEnable = false;

    // 状态标志
    private boolean mOnPreviewing = false;
    private boolean mOnRecording = false;

    private AspectFrameLayout mAspectLayout;
    private CameraSurfaceView mCameraSurfaceView;
    // 顶部Button
    private Button mBtnSetting;
    private Button mBtnViewPhoto;
    private Button mBtnSwitch;
    // 底部layout 和 Button
    private LinearLayout mBottomLayout;
    private Button mBtnStickers;
    private PictureVideoActionButton mBtnTakeOrRecording;
    private Button mBtnFilters;

    private Button mBtnRecordDelete;
    private Button mBtnRecordDone;

    // 显示滤镜
    private boolean isShowingEffect = false;
    private AsyncRecyclerview mEffectListView;
    private LinearLayoutManager mEffectManager;
    // 是否需要滚动
    private boolean mEffectNeedToMove = false;

    // 显示贴纸
    private boolean isShowingStickers = false;

    // 底部指示器
    private List<String> mIndicatorText = new ArrayList<String>();
    private HorizontalIndicatorView mBottomIndicator;

    private AspectRatioType[] mAspectRatio = {
            AspectRatioType.RATIO_4_3,
            AspectRatioType.RATIO_1_1,
            AspectRatioType.Ratio_16_9
    };
    private int mRatioIndex = 0;
    private AspectRatioType mCurrentRatio = mAspectRatio[0];

    private int mColorIndex = 0;

    private boolean isDebug = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 持有当前上下文
        ParamsManager.context = this;
        String phoneName = Build.MODEL;
        if (phoneName.toLowerCase().contains("bullhead")
                || phoneName.toLowerCase().contains("nexus 5x")) {
            TextureRotationUtils.setBackReverse(true);
            ParamsManager.mBackReverse = true;
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);
        mCameraEnable = PermissionUtils.permissionChecking(this,
                Manifest.permission.CAMERA);
        mStorageWriteEnable = PermissionUtils.permissionChecking(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        mRecordSoundEnable = PermissionUtils.permissionChecking(this,
                Manifest.permission.RECORD_AUDIO);
        ParamsManager.canRecordingAudio = mRecordSoundEnable;
        if (mCameraEnable && mStorageWriteEnable) {
            initView();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_CAMERA);
        }
        // Face++请求联网认证
        FaceTrackManager.getInstance().requestFaceNetwork(this);
    }

    private void initView() {
        mAspectLayout = (AspectFrameLayout) findViewById(R.id.layout_aspect);
        mAspectLayout.setAspectRatio(CameraUtils.getCurrentRatio());
        mCameraSurfaceView = new CameraSurfaceView(this);
        mCameraSurfaceView.addScroller(this);
        mCameraSurfaceView.addClickListener(this);
        mAspectLayout.addView(mCameraSurfaceView);
        mAspectLayout.requestLayout();
        mBtnSetting = (Button)findViewById(R.id.btn_setting);
        mBtnSetting.setOnClickListener(this);
        mBtnViewPhoto = (Button) findViewById(R.id.btn_view_photo);
        mBtnViewPhoto.setOnClickListener(this);
        mBtnSwitch = (Button) findViewById(R.id.btn_switch);
        mBtnSwitch.setOnClickListener(this);

        mBtnStickers = (Button) findViewById(R.id.btn_stickers);
        mBtnStickers.setOnClickListener(this);
        mBtnFilters = (Button) findViewById(R.id.btn_filters);
        mBtnFilters.setOnClickListener(this);
        mBottomIndicator = (HorizontalIndicatorView) findViewById(R.id.bottom_indicator);
        String[] galleryIndicator = getResources().getStringArray(R.array.gallery_indicator);
        for (String text : galleryIndicator) {
            mIndicatorText.add(text);
        }
        mBottomIndicator.setIndicators(mIndicatorText);
        mBottomIndicator.addIndicatorListener(this);

        mBtnTakeOrRecording = (PictureVideoActionButton) findViewById(R.id.btn_take);
        mBtnTakeOrRecording.setOnClickListener(this);
        mBtnTakeOrRecording.setActionListener(this);

        mBtnRecordDelete = (Button) findViewById(R.id.btn_record_delete);
        mBtnRecordDelete.setOnClickListener(this);
        mBtnRecordDone = (Button) findViewById(R.id.btn_record_done);
        mBtnRecordDone.setOnClickListener(this);

        mBottomLayout = (LinearLayout) findViewById(R.id.layout_bottom);
        if (CameraUtils.getCurrentRatio() < 0.75f) {
            mBottomLayout.setBackgroundResource(R.drawable.bottom_background_glow);
            mBtnStickers.setBackgroundResource(R.drawable.gallery_sticker_glow);
            mBtnFilters.setBackgroundResource(R.drawable.gallery_filter_glow);
            mBtnRecordDelete.setBackgroundResource(R.drawable.preview_video_delete_white);
            mBtnRecordDone.setBackgroundResource(R.drawable.preview_video_done_white);
        } else {
            mBottomLayout.setBackgroundResource(R.drawable.bottom_background);
            mBtnStickers.setBackgroundResource(R.drawable.gallery_sticker);
            mBtnFilters.setBackgroundResource(R.drawable.gallery_filter);
            mBtnRecordDelete.setBackgroundResource(R.drawable.preview_video_delete_black);
            mBtnRecordDone.setBackgroundResource(R.drawable.preview_video_done_black);
        }

        initEffectListView();

    }

    /**
     * 初始化滤镜显示
     */
    private void initEffectListView() {
        // 初始化滤镜图片
        mEffectListView = (AsyncRecyclerview) findViewById(R.id.effect_list);
        mEffectListView.setVisibility(View.GONE);
        mEffectManager = new LinearLayoutManager(this);
        mEffectManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mEffectListView.setLayoutManager(mEffectManager);

        EffectFilterAdapter adapter = new EffectFilterAdapter(this,
                ColorFilterManager.getInstance().getFilterType(),
                ColorFilterManager.getInstance().getFilterName());

        mEffectListView.setAdapter(adapter);
        adapter.addItemClickListener(new EffectFilterAdapter.OnItemClickLitener() {
            @Override
            public void onItemClick(int position) {
                mColorIndex = position;
                DrawerManager.getInstance().changeFilterType(
                        ColorFilterManager.getInstance().getColorFilterType(position));
                if (isDebug) {
                    Log.d("changeFilter", "index = " + mColorIndex + ", filter name = "
                            + ColorFilterManager.getInstance().getColorFilterName(mColorIndex));
                }
            }
        });
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.CAMERA }, REQUEST_CAMERA);
    }

    private void requestStorageWritePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE_WRITE);
    }

    private void requestRecordPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.RECORD_AUDIO }, REQUEST_RECORD);
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[] {
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                },
                REQUEST_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            // 相机权限
            case REQUEST_CAMERA:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mCameraEnable = true;
                    initView();
                }
                break;

            // 存储权限
            case REQUEST_STORAGE_WRITE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mStorageWriteEnable = true;
                }
                break;

            // 录音权限
            case REQUEST_RECORD:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mRecordSoundEnable = true;
                    ParamsManager.canRecordingAudio = true;
                }
                break;

            // 位置权限
            case REQUEST_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationEnable = true;
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerHomeReceiver();
        if (mCameraEnable) {
            DrawerManager.getInstance().startPreview();
            mOnPreviewing = true;
        } else {
            requestCameraPermission();
        }
        // 判断是否允许写入权限
        if (PermissionUtils.permissionChecking(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            mStorageWriteEnable = true;
        }
        if (isShowingEffect) {
            scrollToCurrentEffect();
        }
    }

    @Override
    public void onBackPressed() {
        if (isShowingEffect) {
            isShowingEffect = false;
            if (mEffectListView != null) {
                mEffectListView.setVisibility(View.GONE);
            }
            return;
        }
        if (isShowingStickers) {
            isShowingStickers = false;
            // TODO 隐藏贴纸选项

            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unRegisterHomeReceiver();
        if (mCameraEnable) {
            DrawerManager.getInstance().stopPreview();
            mOnPreviewing = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在停止时需要释放上下文，防止内存泄漏
        ParamsManager.context = null;
    }

    private void registerHomeReceiver() {
        IntentFilter homeFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomePressReceiver, homeFilter);
    }

    private void unRegisterHomeReceiver() {
        unregisterReceiver(mHomePressReceiver);
    }

    /**
     * 监听点击home键
     */
    private BroadcastReceiver mHomePressReceiver = new BroadcastReceiver() {
        private final String SYSTEM_DIALOG_REASON_KEY = "reason";
        private final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (TextUtils.isEmpty(reason)) {
                    return;
                }
                // 当点击了home键时需要停止预览，防止后台一直持有相机
                if (reason.equals(SYSTEM_DIALOG_REASON_HOME_KEY)) {
                    if (mOnPreviewing) {
                        DrawerManager.getInstance().stopPreview();
                    }
                }
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            // 查看图库
            case R.id.btn_view_photo:
                viewPhoto();
                break;

            // 切换相机
            case R.id.btn_switch:
                switchCamera();
                break;

            // 设置
            case R.id.btn_setting:
                showSettingPopView();
                break;

            // 显示贴纸
            case R.id.btn_stickers:
                showStickers();
                break;

            // 显示滤镜
            case R.id.btn_filters:
                showFilters();
                break;

            // 拍照或录制
            case R.id.btn_take:
                takePicture();
                break;

            // 删除
            case R.id.btn_record_delete:
                deleteRecordedVideo();
                break;

            // 完成录制，进入预览
            case R.id.btn_record_done:
                previewRedcordedvideo();
                break;
        }
    }

    @Override
    public void swipeBack() {
        mColorIndex++;
        if (mColorIndex >= ColorFilterManager.getInstance().getColorFilterCount()) {
            mColorIndex = 0;
        }
        DrawerManager.getInstance()
                .changeFilterType(ColorFilterManager.getInstance().getColorFilterType(mColorIndex));
        scrollToCurrentEffect();
        if (isDebug) {
            Log.d("changeFilter", "index = " + mColorIndex + ", filter name = "
                    + ColorFilterManager.getInstance().getColorFilterName(mColorIndex));
        }
    }

    @Override
    public void swipeFrontal() {
        mColorIndex--;
        if (mColorIndex < 0) {
            int count = ColorFilterManager.getInstance().getColorFilterCount();
            mColorIndex = count > 0 ? count - 1 : 0;
        }
        DrawerManager.getInstance()
                .changeFilterType(ColorFilterManager.getInstance().getColorFilterType(mColorIndex));

        scrollToCurrentEffect();

        if (isDebug) {
            Log.d("changeFilter", "index = " + mColorIndex + ", filter name = "
                    + ColorFilterManager.getInstance().getColorFilterName(mColorIndex));
        }
    }

    /**
     * 滚动到当前位置
     */
    private void scrollToCurrentEffect() {
        if (isShowingEffect) {
            Log.d("scrollToCurrentEffect", "hahaha");
            int firstItem = mEffectManager.findFirstVisibleItemPosition();
            int lastItem = mEffectManager.findLastVisibleItemPosition();
            if (mColorIndex <= firstItem) {
                mEffectListView.scrollToPosition(mColorIndex);
            } else if (mColorIndex <= lastItem) {
                int top = mEffectListView.getChildAt(mColorIndex - firstItem).getTop();
                mEffectListView.scrollBy(0, top);
            } else {
                mEffectListView.scrollToPosition(mColorIndex);
                mEffectNeedToMove = true;
            }
        }
    }

    @Override
    public void swipeUpper(boolean startInLeft) {
        Log.d(TAG, "swipeUpper, startInLeft ? " + startInLeft);
    }

    @Override
    public void swipeDown(boolean startInLeft) {
        Log.d(TAG, "swipeDown, startInLeft ? " + startInLeft);
    }

    @Override
    public void onClick(float x, float y) {
        surfaceViewClick(x, y);
    }

    @Override
    public void doubleClick(float x, float y) {
    }

    /**
     * 点击SurfaceView
     * @param x x轴坐标
     * @param y y轴坐标
     */
    private void surfaceViewClick(float x, float y) {
        if (isShowingEffect) {
            isShowingEffect = false;
            mEffectListView.setVisibility(View.GONE);
        }
        DrawerManager.getInstance().setFocusAres(focusOnTouch((int)x, (int)y));
    }

    /**
     * 查看媒体库中的图片
     */
    private void viewPhoto() {
        startActivity(new Intent(CameraActivity.this, PhotoViewActivity.class));
    }

    /**
     * 拍照
     */
    private void takePicture() {
        if (!mOnPreviewing) {
            return;
        }
        if (mStorageWriteEnable
                || PermissionUtils.permissionChecking(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            if (ParamsManager.mGalleryType == GalleryType.PICTURE) {
                DrawerManager.getInstance().takePicture();
            }
        } else {
            requestStorageWritePermission();
        }
    }

    /**
     * 开始录制
     */
    @Override
    public void startRecord() {

        // 设置输出路径
        String path = ParamsManager.VideoPath
                + "CainCamera_" + System.currentTimeMillis() + ".mp4";
        RecorderManager.getInstance().setOutputPath(path);
        // 是否允许录音
        RecorderManager.getInstance().setEnableAudioRecording(mRecordSoundEnable);
        // 是否允许高清录制
        RecorderManager.getInstance().enableHighDefinition(true);
        // 初始化录制器
        RecorderManager.getInstance().initRecorder(RecorderManager.RECORD_WIDTH,
                RecorderManager.RECORD_HEIGHT, mEncoderListener);

        // 隐藏删除按钮
        mBtnRecordDelete.setVisibility(View.GONE);
    }

    /**
     * 停止录制
     */
    @Override
    public void stopRecord() {
        DrawerManager.getInstance().stopRecording();
    }

    @Override
    public boolean enableChangeState() {
        return mEnableToChangeState;
    }

    /**
     * 录制监听器
     */
    private MediaEncoder.MediaEncoderListener
            mEncoderListener = new MediaEncoder.MediaEncoderListener() {

        @Override
        public void onPrepared(MediaEncoder encoder) {
            if (encoder instanceof MediaVideoEncoder) {
                // 准备完成，开始录制
                DrawerManager.getInstance().startRecording();
            }
        }

        @Override
        public void onStarted(MediaEncoder encoder) {
            // MediaCodec已经处于开始录制阶段，此时允许改变状态
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 显示视频预览按钮
                    mBtnRecordDone.setVisibility(View.VISIBLE);
                    // 允许改变状态
                    mEnableToChangeState = true;
                    mOnRecording = true;
                }
            });
        }

        @Override
        public void onStopped(MediaEncoder encoder) {
            mEnableToChangeState = false;
            mOnRecording = false;
        }

        @Override
        public void onReleased(MediaEncoder encoder) { // 复用器释放完成
            if (encoder instanceof MediaVideoEncoder) {
                // 录制完成跳转预览页面
                String outputPath = RecorderManager.getInstance().getOutputPath();
                mListPath.add(outputPath);
                // 清空原来的路径
                RecorderManager.getInstance().setOutputPath(null);

                // 显示删除按钮
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mOnRecording) {
                            mBtnRecordDelete.setVisibility(View.GONE);
                        } else {
                            mBtnRecordDelete.setVisibility(View.VISIBLE);
                        }
                    }
                });

                // 允许改变状态
                mEnableToChangeState = true;

                // 处于录制状态点击了预览按钮，则需要等待完成再跳转
                if (mNeedToWaitStop) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 重置按钮状态
                            mBtnTakeOrRecording.resetStatus();
                            // 开始预览
                            previewRedcordedvideo();
                        }
                    });
                }
            }


        }
    };

    private ArrayList<String> mListPath = new ArrayList<String>();
    /**
     * 删除录制的视频
     */
    synchronized private void deleteRecordedVideo() {
        if (mListPath.size() > 0) {
            // 删除文件
            String path = mListPath.get(mListPath.size()-1);
            if (!TextUtils.isEmpty(path)) {
                FileUtils.deleteFile(path);
            }
            mListPath.remove(mListPath.size() - 1);
            // 如果此时没有录制好的视频路径，则隐藏删除和预览按钮
            if (mListPath.size() == 0) {
                mBtnRecordDelete.setVisibility(View.GONE);
                mBtnRecordDone.setVisibility(View.GONE);
                // 复位状态
                mEnableToChangeState = true;
                mNeedToWaitStop = false;
                mOnRecording = false;
            }
        }
    }

    // 是否允许按钮改变状态
    private boolean mEnableToChangeState = true;

    // 是否需要等待录制完成再跳转
    private boolean mNeedToWaitStop = false;

    /**
     * 预览视频
     */
    private void previewRedcordedvideo() {
        if (mOnRecording) {
            mNeedToWaitStop = true;
            // 停止录制
            DrawerManager.getInstance().stopRecording();
        } else {
            mNeedToWaitStop = false;
            Intent intent = new Intent(CameraActivity.this, CapturePreviewActivity.class);
            intent.putStringArrayListExtra(CapturePreviewActivity.PATH, mListPath);
            startActivity(intent);
            // 清空路径，预览页面返回时会清空所有缓存的数据
            mListPath.clear();
            // 隐藏视频预览和删除按钮
            mBtnRecordDone.setVisibility(View.GONE);
            mBtnRecordDelete.setVisibility(View.GONE);
        }
    }

    /**
     * 切换相机
     */
    private void switchCamera() {
        if (!mCameraEnable) {
            requestCameraPermission();
            return;
        }
        if (mCameraSurfaceView != null) {
            DrawerManager.getInstance().switchCamera();
        }
    }

    /**
     * 显示设置更多视图
     */
    private void showSettingPopView() {

    }

    /**
     * 显示贴纸列表
     */
    private void showStickers() {

    }

    /**
     * 显示滤镜
     */
    private void showFilters() {
        isShowingEffect = true;
        if (mEffectListView != null) {
            mEffectListView.setVisibility(View.VISIBLE);
            // TODO 如果是先滑动滤镜再显示，调用scrollToCurrentEffect会数据越界，后续再解决
        }
    }

    @Override
    public void onIndicatorChanged(int currentIndex) {
        if (currentIndex == 0) {
            ParamsManager.mGalleryType = GalleryType.GIF;
            // TODO GIF录制后面再做处理
            mBtnTakeOrRecording.setIsRecorder(false);
        } else if (currentIndex == 1) {
            ParamsManager.mGalleryType = GalleryType.PICTURE;
            // 拍照状态
            mBtnTakeOrRecording.setIsRecorder(false);
        } else if (currentIndex == 2) {
            ParamsManager.mGalleryType = GalleryType.VIDEO;
            // 录制视频状态
            mBtnTakeOrRecording.setIsRecorder(true);

            // 清空当前的视频路径
            mListPath.clear();
            // 请求录音权限
            if (!mRecordSoundEnable) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD);
            }
        }
    }

    /**
     * 计算触摸区域
     * @param x
     * @param y
     * @return
     */
    private Rect focusOnTouch(int x, int y) {
        Rect rect = new Rect(x - FocusSize, y - FocusSize,
                x + FocusSize, y + FocusSize);
        int left = rect.left * 2000 / mCameraSurfaceView.getWidth() - 1000;
        int top = rect.top * 2000 / mCameraSurfaceView.getHeight() - 1000;
        int right = rect.right * 2000 / mCameraSurfaceView.getWidth() - 1000;
        int bottom = rect.bottom * 2000 / mCameraSurfaceView.getHeight() - 1000;
        // 归整到(-1000, -1000) 到 (1000, 1000)的区域内
        left = left < -1000 ? -1000 : left;
        top = top < -1000 ? -1000 : top;
        right = right > 1000 ? 1000 : right;
        bottom = bottom > 1000 ? 1000 : bottom;
        return new Rect(left, top, right, bottom);
    }
}
