package com.example.secaicontainerengine.config;

import com.example.secaicontainerengine.handler.UploadStatusWebSocketHandler;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.SftpConnect;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.util.Vector;

import static com.example.secaicontainerengine.util.FileUtils.processFilesInRemoteDirectory;

@Component
@Slf4j
public class SftpUploader {


    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String flag;
    private final ModelEvaluationService modelEvaluationService;
    private final UploadStatusWebSocketHandler uploadStatusWebSocketHandler;

    public SftpUploader(
            @Value("${sftp.host}") String host,
            @Value("${sftp.port}") int port,
            @Value("${sftp.username}") String username,
            @Value("${sftp.password}") String password,
            @Value("${sftp.flag}") String flag, ModelEvaluationService modelEvaluationService,
            UploadStatusWebSocketHandler uploadStatusWebSocketHandler) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.flag = flag;
        this.modelEvaluationService = modelEvaluationService;
        this.uploadStatusWebSocketHandler = uploadStatusWebSocketHandler;
    }

    public SftpConnect connectNfs() throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        SftpConnect sftpConnect = new SftpConnect();

        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpConnect.setSftpChannel(sftpChannel);
        sftpConnect.setSession(session);
        return sftpConnect;
    }

    public void uploadDirectory(Long userId, String localDir, String remoteDir, Long modelId) throws Exception {

        SftpConnect sftpConnect = connectNfs();
        ChannelSftp sftpChannel = sftpConnect.getSftpChannel();
        Session session = sftpConnect.getSession();
        sftpChannel.connect();

        try {

            // 确保远程目录存在
            createRemoteDirectory(sftpChannel, remoteDir);

            //上传模型数据
            uploadDirectoryRecursive(sftpChannel, new File(localDir), remoteDir);

//            log.info("上传到nfs服务器成功");

            // 更新数据库
            ModelMessage modelMessage = modelEvaluationService.getById(modelId);

            // 获取到上传文件的路径
            // 获取远程目录中的文件和文件夹
            Vector<ChannelSftp.LsEntry> files = sftpChannel.ls(remoteDir);
            String remoteModelPath = remoteDir + File.separator + files.get(0).getFilename();
            modelMessage.setAllDataAddress(remoteModelPath);
            processFilesInRemoteDirectory(sftpChannel, modelMessage, remoteModelPath);
            modelEvaluationService.updateById(modelMessage);

            //上传完毕后，删除本地目录
            deleteLocalDirectory(new File(localDir));

            // 当上传成功之后将信息通过websocket回弹到前端
            uploadStatusWebSocketHandler.sendUploadStatus(String.valueOf(userId), "上传完成!" + String.valueOf(modelMessage.getId()));

        }catch (Exception e){
            log.error("上传失败，错误信息：{}", e.getMessage());
            uploadStatusWebSocketHandler.sendUploadStatus(String.valueOf(userId), "上传失败");
        } finally {
            sftpChannel.disconnect();
            session.disconnect();
        }
    }

    private void uploadDirectoryRecursive(ChannelSftp sftp, File localFile, String remoteDir) throws Exception {
        if (!localFile.exists()) {
            throw new IllegalArgumentException("本地目录或文件不存在: " + localFile.getAbsolutePath());
        }

        if (localFile.isDirectory()) {
            try {
                sftp.mkdir(remoteDir);
            } catch (Exception ignored) {
                // 如果目录已存在，忽略异常
            }

            for (File file : localFile.listFiles()) {
                String remotePath = remoteDir + "/" + file.getName();
                uploadDirectoryRecursive(sftp, file, remotePath);
            }
        } else {
            sftp.put(localFile.getAbsolutePath(), remoteDir);
        }
    }

    private void deleteLocalDirectory(File directory) {
        if (directory.isDirectory()) {
            // 递归删除子文件和子目录
            for (File file : directory.listFiles()) {
                deleteLocalDirectory(file);
            }
        }
        // 删除文件或空目录
        if (directory.delete()) {
            log.info("已删除: {}", directory.getAbsolutePath());
        } else {
            log.warn("删除失败: {}", directory.getAbsolutePath());
        }
    }

    // 上传单个文件
    public void uploadFile(String localFilePath, String remoteFilePath) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect();

        try {
            sftpChannel.put(localFilePath, remoteFilePath);
        } finally {
            sftpChannel.disconnect();
            session.disconnect();
        }
    }

    // 创建标志文件
    private File createFlagFile(String localDir, String flagFileName) throws Exception {
        File flagFile = new File(localDir + "/" + flagFileName);
        try (FileWriter writer = new FileWriter(flagFile)) {
            writer.write("Upload completed at: " + System.currentTimeMillis());
        }
        return flagFile;
    }

    // 创建远程目录（递归创建）
    public void createRemoteDirectory(ChannelSftp sftpChannel, String remoteDir) throws SftpException {
        String[] directories = remoteDir.split("/");
        StringBuilder currentPath = new StringBuilder();

        for (String dir : directories) {
            if (dir.isEmpty()) {
                continue; // 跳过空路径片段（如以 "/" 开头的路径）
            }

            currentPath.append("/").append(dir);
            try {
                sftpChannel.cd(currentPath.toString()); // 尝试进入目录
            } catch (SftpException e) {
                // 如果目录不存在，创建它
                sftpChannel.mkdir(currentPath.toString());
                sftpChannel.cd(currentPath.toString()); // 创建后进入目录
            }
        }

    }
}
