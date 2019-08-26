package com.xyoye.dandanplay.ui.activities.personal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;

import com.blankj.utilcode.util.ServiceUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.xyoye.dandanplay.R;
import com.xyoye.dandanplay.base.BaseMvpActivity;
import com.xyoye.dandanplay.mvp.impl.DownloadManagerPresenterImpl;
import com.xyoye.dandanplay.mvp.presenter.DownloadManagerPresenter;
import com.xyoye.dandanplay.mvp.view.DownloadManagerView;
import com.xyoye.dandanplay.torrent.TorrentEngine;
import com.xyoye.dandanplay.torrent.TorrentService;
import com.xyoye.dandanplay.torrent.info.TaskStateBean;
import com.xyoye.dandanplay.torrent.info.Torrent;
import com.xyoye.dandanplay.ui.activities.anime.TrackerActivity;
import com.xyoye.dandanplay.ui.fragment.DownloadedFragment;
import com.xyoye.dandanplay.ui.fragment.DownloadingFragment;
import com.xyoye.dandanplay.ui.weight.dialog.CommonDialog;
import com.xyoye.dandanplay.ui.weight.indicator.LinePagerIndicator;
import com.xyoye.dandanplay.ui.weight.indicator.MagicIndicator;
import com.xyoye.dandanplay.ui.weight.indicator.abs.CommonNavigatorAdapter;
import com.xyoye.dandanplay.ui.weight.indicator.abs.IPagerIndicator;
import com.xyoye.dandanplay.ui.weight.indicator.abs.IPagerTitleView;
import com.xyoye.dandanplay.ui.weight.indicator.navigator.CommonNavigator;
import com.xyoye.dandanplay.ui.weight.indicator.title.ColorTransitionPagerTitleView;
import com.xyoye.dandanplay.ui.weight.indicator.title.SimplePagerTitleView;
import com.xyoye.dandanplay.utils.TaskManageListener;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;

/**
 * Created by xyoye on 2019/8/1.
 */

public class DownloadManagerActivity extends BaseMvpActivity<DownloadManagerPresenter> implements DownloadManagerView, TaskManageListener {
    public static final int TASK_DOWNLOADING_DANMU_BIND = 1001;
    public static final int TASK_DOWNLOADED_DANMU_BIND = 1002;

    @BindView(R.id.indicator)
    MagicIndicator magicIndicator;
    @BindView(R.id.viewpager)
    ViewPager viewPager;

    private List<Fragment> fragmentList;
    private List<TaskStateBean> mTaskStateList;
    private int selectedPosition = 0;

    @NonNull
    @Override
    protected DownloadManagerPresenter initPresenter() {
        return new DownloadManagerPresenterImpl(this, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected int initPageLayoutID() {
        return R.layout.activity_download_manager_v2;
    }

    @Override
    public void initView() {
        setTitle("下载管理");
        fragmentList = new ArrayList<>();
        mTaskStateList = new ArrayList<>();
        DownloadedFragment downloadedFragment = DownloadedFragment.newInstance();
        DownloadingFragment downloadingFragment = DownloadingFragment.newInstance();
        downloadingFragment.setTaskManageListener(this);
        fragmentList.add(downloadingFragment);
        fragmentList.add(downloadedFragment);

        initIndicator();

        initViewPager();

        int position = getIntent().getIntExtra("fragment_position", -1);
        if (position == 1){
            magicIndicator.onPageSelected(0);
        }else if (position == 2){
            magicIndicator.onPageSelected(1);
        }

        if (ServiceUtils.isServiceRunning(TorrentService.class)) {
            startNewTask();
        } else {
            startTorrentService();
            presenter.observeService();
        }
    }

    @Override
    public void initListener() {

    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.about_download:
                new CommonDialog.Builder(DownloadManagerActivity.this)
                        .hideCancel()
                        .setAutoDismiss()
                        .build()
                        .show(getResources().getString(R.string.about_download), "关于下载", "确定", "");
                break;
            case R.id.tracker_manager:
                startActivity(new Intent(DownloadManagerActivity.this, TrackerActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_download_manager_v2, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void showLoading() {
        showLoadingDialog();
    }

    @Override
    public void hideLoading() {
        dismissLoadingDialog();
    }

    @Override
    public void showError(String message) {
        ToastUtils.showShort(message);
    }


    private void initIndicator() {
        List<String> titleList = new ArrayList<>();
        titleList.add("下载中");
        titleList.add("已完成");
        CommonNavigator commonNavigator = new CommonNavigator(this);
        commonNavigator.setAdjustMode(true);
        commonNavigator.setAdapter(new CommonNavigatorAdapter() {
            @Override
            public int getCount() {
                return titleList.size();
            }

            @Override
            public IPagerTitleView getTitleView(Context context, int index) {
                SimplePagerTitleView simplePagerTitleView = new ColorTransitionPagerTitleView(context);
                simplePagerTitleView.setText(titleList.get(index));
                simplePagerTitleView.setNormalColor(ContextCompat.getColor(context, R.color.text_black));
                simplePagerTitleView.setSelectedColor(ContextCompat.getColor(context, R.color.theme_color));
                simplePagerTitleView.setOnClickListener(v -> viewPager.setCurrentItem(index));
                return simplePagerTitleView;
            }

            @SuppressLint("ResourceType")
            @Override
            public IPagerIndicator getIndicator(Context context) {
                LinePagerIndicator indicator = new LinePagerIndicator(context);
                indicator.setColors(ContextCompat.getColor(context, R.color.theme_color));
                return indicator;
            }
        });
        magicIndicator.setNavigator(commonNavigator);
        magicIndicator.onPageSelected(selectedPosition);
    }

    private void initViewPager() {
        DownloadFragmentAdapter fragmentAdapter = new DownloadFragmentAdapter(getSupportFragmentManager(), fragmentList);
        viewPager.setAdapter(fragmentAdapter);
        viewPager.setCurrentItem(selectedPosition);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                magicIndicator.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }

            @Override
            public void onPageSelected(int position) {
                magicIndicator.onPageSelected(position);
                selectedPosition = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                magicIndicator.onPageScrollStateChanged(state);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK){
            if (requestCode == TASK_DOWNLOADING_DANMU_BIND){
                int episodeId = data.getIntExtra("episode_id", -1);
                String danmuPath = data.getStringExtra("path");
                String taskHash = data.getStringExtra("task_hash");
                int taskFilePosition = data.getIntExtra("task_file_position", -1);
                Torrent torrent = TorrentEngine.getInstance().getTask(taskHash).getTorrent();
                Torrent.TorrentFile torrentFile = torrent.getChildFileList().get(taskFilePosition);
                torrentFile.setDanmuEpisodeId(episodeId);
                torrentFile.setDanmuFilePath(danmuPath);
            }else if (requestCode == TASK_DOWNLOADED_DANMU_BIND){
                String danmuPath = data.getStringExtra("path");
                int episodeId = data.getIntExtra("episode_id", -1);
                int taskPosition = data.getIntExtra("position", -1);
                int taskFilePosition = data.getIntExtra("task_file_position", -1);

            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //没有下载任务在执行，关闭服务
        if (!TorrentEngine.getInstance().hasTasks()) {
            if (ServiceUtils.isServiceRunning(TorrentService.class)) {
                ServiceUtils.stopService(TorrentService.class);
            }
        }
    }

    /**
     * 开启新任务
     */
    @Override
    public void startNewTask() {
        Torrent torrent = getIntent().getParcelableExtra("new_task");
//        if (torrent != null &&
//                !IApplication.taskMap.containsKey(torrent.getHash()) &&
//                !IApplication.taskFinishHashList.contains(torrent.getHash())) {
//            EventBus.getDefault().post(new TorrentStartEvent(torrent));
//        }
    }

    /**
     * 开启下载服务
     */
    private void startTorrentService() {
        Intent intent = new Intent(this, TorrentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void pauseTask(String taskHash) {
        TorrentEngine.getInstance().getTask(taskHash).pause();
    }

    @Override
    public void resumeTask(String taskHash) {
        TorrentEngine.getInstance().getTask(taskHash).resume();
    }

    @Override
    public void deleteTask(String taskHash, boolean withFile) {
        TorrentEngine.getInstance().getTask(taskHash).remove(withFile);
    }

    @Override
    public List<TaskStateBean> getTaskList() {
        return mTaskStateList;
    }

    private class DownloadFragmentAdapter extends FragmentPagerAdapter {
        private List<Fragment> list;

        private DownloadFragmentAdapter(FragmentManager supportFragmentManager, List<Fragment> list) {
            super(supportFragmentManager);
            this.list = list;
        }

        @Override
        public Fragment getItem(int position) {
            return list.get(position);
        }

        @Override
        public int getCount() {
            return list.size();
        }
    }
}
