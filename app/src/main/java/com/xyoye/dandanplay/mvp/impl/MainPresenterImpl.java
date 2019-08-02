package com.xyoye.dandanplay.mvp.impl;

import android.database.Cursor;
import android.os.Bundle;

import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.StringUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.xyoye.dandanplay.app.IApplication;
import com.xyoye.dandanplay.base.BaseMvpPresenterImpl;
import com.xyoye.dandanplay.bean.AnimeTypeBean;
import com.xyoye.dandanplay.bean.SubGroupBean;
import com.xyoye.dandanplay.database.DataBaseManager;
import com.xyoye.dandanplay.mvp.presenter.MainPresenter;
import com.xyoye.dandanplay.mvp.view.MainView;
import com.xyoye.dandanplay.utils.AppConfig;
import com.xyoye.dandanplay.utils.CloudFilterHandler;
import com.xyoye.dandanplay.utils.Constants;
import com.xyoye.dandanplay.utils.Lifeful;
import com.xyoye.dandanplay.utils.TrackerManager;
import com.xyoye.dandanplay.utils.jlibtorrent.BtTask;
import com.xyoye.dandanplay.utils.jlibtorrent.Torrent;
import com.xyoye.dandanplay.utils.jlibtorrent.TorrentUtil;
import com.xyoye.dandanplay.utils.net.CommOtherDataObserver;
import com.xyoye.dandanplay.utils.net.NetworkConsumer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Created by xyoye on 2018/6/28 0028.
 */

public class MainPresenterImpl extends BaseMvpPresenterImpl<MainView> implements MainPresenter {

    public MainPresenterImpl(MainView view, Lifeful lifeful) {
        super(view, lifeful);
    }

    @Override
    public void init() {

    }

    @Override
    public void process(Bundle savedInstanceState) {
        initDownloadingTask();
        initDownloadedTask();
        initAnimeType();
        initSubGroup();
        long lastUpdateTime = AppConfig.getInstance().getUpdateFilterTime();
        long nowTime = System.currentTimeMillis();
        //七天更新一次云过滤列表
        if (nowTime - lastUpdateTime > 7 * 24 * 60 * 60 * 1000){
            AppConfig.getInstance().setUpdateFilterTime(nowTime);
            initCloudFilter();
        }else {
            getCloudFilter();
        }
    }

    @Override
    public void resume() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void destroy() {

    }

    //Tracker
    @Override
    public void initTracker(){
      IApplication.getExecutor().execute(() -> {
            //trackers数据
            File configFolder = new File(FileUtils.getDirName(Constants.DefaultConfig.configPath));
            if (configFolder.isFile())
                configFolder.delete();
            if (!configFolder.exists())
                configFolder.mkdirs();

            File trackerFile = new File(Constants.DefaultConfig.configPath);

            //文件不存在，读取asset中默认的trackers，并写入文件
            if (!trackerFile.exists()){
                TrackerManager.resetTracker();
            }
            //文件存在，直接读取
            else {
                TrackerManager.queryTracker();
            }
        });
    }

    /**
     * 恢复下载中任务
     */
    private void initDownloadingTask(){
        if (IApplication.taskList.size() > 0)
            return;
        IApplication.getExecutor().execute(() -> {
            Cursor cursor = TorrentUtil.queryDBTorrent();
            while (cursor.moveToNext()) {
                Torrent torrent = new Torrent();
                torrent.setTorrentPath(cursor.getString(1));
                torrent.setAnimeTitle(cursor.getString(2));
                torrent.setMagnet(cursor.getString(3));
                torrent.setFinished(cursor.getInt(4) == 1);
                String prioritiesSaveData = cursor.getString(5);
                String[] priorities;
                if (prioritiesSaveData.contains(";")) {
                    priorities = prioritiesSaveData.split(";");
                } else {
                    priorities = new String[]{prioritiesSaveData};
                }

                //根据路径读取torrent信息
                TorrentInfo torrentInfo = TorrentUtil.getTorrentInfoForFile(torrent.getTorrentPath());
                if (torrentInfo == null)
                    continue;
                if (priorities.length != torrentInfo.numFiles())
                    continue;

                String saveDirPath = AppConfig.getInstance().getDownloadFolder() +
                        ((StringUtils.isEmpty(torrent.getAnimeTitle()))
                                ? ("/" + torrentInfo.name())
                                : ("/" + torrent.getAnimeTitle()));
                torrent.setSaveDirPath(saveDirPath);
                torrent.setHash(torrentInfo.infoHash().toString());
                torrent.setTitle(torrentInfo.name());
                torrent.setLength(torrentInfo.totalSize());
                List<Torrent.TorrentFile> torrentFileList = new ArrayList<>();

                for (int i = 0; i < priorities.length; i++) {
                    Torrent.TorrentFile torrentFile = new Torrent.TorrentFile();
                    torrentFile.setName(torrentInfo.files().fileName(i));
                    torrentFile.setPath(saveDirPath + "/" + torrentInfo.files().filePath(i));
                    torrentFile.setLength(torrentInfo.files().fileSize(i));
                    torrentFile.setChecked("1".equals(priorities[i]));
                    torrentFileList.add(torrentFile);
                }
                torrent.setTorrentFileList(torrentFileList);

                BtTask btTask = new BtTask(torrent);
                IApplication.taskList.add(btTask);
                IApplication.taskMap.put(torrent.getHash(), IApplication.taskList.size()-1);
            }
        });
    }

    /**
     * 恢复已完成任务
     */
    private void initDownloadedTask(){
        if (IApplication.taskFinishHashList.size() > 0)
            return;
        IApplication.getExecutor().execute(() -> {
            Cursor taskCursor = DataBaseManager
                    .getInstance()
                    .selectTable(14)
                    .query()
                    .execute();
            while (taskCursor.moveToNext()) {
                String taskHash = taskCursor.getString(5);
                IApplication.taskFinishHashList.add(taskHash);
            }
        });
    }

    //番剧分类
    private void initAnimeType(){
        AnimeTypeBean.getAnimeType(new CommOtherDataObserver<AnimeTypeBean>(getLifeful()) {
            @Override
            public void onSuccess(AnimeTypeBean animeTypeBean) {
                if (animeTypeBean != null && animeTypeBean.getTypes() != null && animeTypeBean.getTypes().size() > 0){
                    DataBaseManager.getInstance()
                            .selectTable(4)
                            .delete()
                            .execute();
                    DataBaseManager.getInstance()
                            .selectTable(4)
                            .insert()
                            .param(1, -1)
                            .param(2, "全部")
                            .execute();
                    for (AnimeTypeBean.TypesBean typesBean : animeTypeBean.getTypes()){
                        DataBaseManager.getInstance()
                                .selectTable(4)
                                .insert()
                                .param(1, typesBean.getId())
                                .param(2, typesBean.getName())
                                .execute();
                    }
                }
            }

            @Override
            public void onError(int errorCode, String message) {
                LogUtils.e(message);
                ToastUtils.showShort(message);
            }
        }, new NetworkConsumer());
    }

    //字幕组
    private void initSubGroup(){
        SubGroupBean.getSubGroup(new CommOtherDataObserver<SubGroupBean>(getLifeful()) {
            @Override
            public void onSuccess(SubGroupBean subGroupBean) {
                if (subGroupBean != null && subGroupBean.getSubgroups() != null && subGroupBean.getSubgroups().size() > 0){

                    DataBaseManager.getInstance()
                            .selectTable(5)
                            .delete()
                            .execute();

                    DataBaseManager.getInstance()
                            .selectTable(5)
                            .insert()
                            .param(1, -1)
                            .param(2, "全部")
                            .execute();

                    for (SubGroupBean.SubgroupsBean subgroupsBean : subGroupBean.getSubgroups()){
                        DataBaseManager.getInstance()
                                .selectTable(5)
                                .insert()
                                .param(1, subgroupsBean.getId())
                                .param(2, subgroupsBean.getName())
                                .execute();
                    }
                }
            }

            @Override
            public void onError(int errorCode, String message) {
                LogUtils.e(message);
                ToastUtils.showShort(message);
            }
        }, new NetworkConsumer());
    }

    //弹幕云过滤
    private void initCloudFilter(){
        IApplication.getExecutor().execute(() -> {
            List<String> filters = getFilterString();
            IApplication.cloudFilterList.addAll(filters);

            DataBaseManager.getInstance()
                    .selectTable(10)
                    .delete()
                    .execute();
            for (int i=0; i<filters.size(); i++){
                DataBaseManager.getInstance()
                        .selectTable(10)
                        .insert()
                        .param(1, filters.get(i))
                        .execute();
            }
        });
    }

    //获取保存的云过滤数据
    private void getCloudFilter(){
        //云屏蔽数据
        List<String> cloudFilter = new ArrayList<>();
        Cursor cursor = DataBaseManager.getInstance()
                        .selectTable(10)
                        .query()
                        .setColumns(1)
                        .execute();

        while (cursor.moveToNext()){
            String text = cursor.getString(0);
            cloudFilter.add(text);
        }
        cursor.close();
        IApplication.cloudFilterList.addAll(cloudFilter);
    }

    /**
     * 下载xml
     */
    private List<String> getFilterString(){
        InputStream is = null;
        List<String> filter = new ArrayList<>();
        try {
            String xmlUrl = "https://api.acplay.net/config/filter.xml";
            URL url = new URL(xmlUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                is = conn.getInputStream();
                SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
                saxParser.parse(is, new CloudFilterHandler(filter::addAll));
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                if (is != null)
                    is.close();
            }catch (IOException e){
                e.printStackTrace();
            }

        }
        return filter;
    }
}
