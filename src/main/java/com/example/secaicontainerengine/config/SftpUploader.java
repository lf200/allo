package com.example.secaicontainerengine.config;

import com.example.secaicontainerengine.handler.UploadStatusWebSocketHandler;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.SftpConnect;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelMessageService;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.LocalDateTime;
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
    private final ModelMessageService modelMessageService;
    private final UploadStatusWebSocketHandler uploadStatusWebSocketHandler;

    public SftpUploader(
            @Value("${sftp.host}") String host,
            @Value("${sftp.port}") int port,
            @Value("${sftp.username}") String username,
            @Value("${sftp.password}") String password,
            @Value("${sftp.flag}") String flag, ModelMessageService modelMessageService,
            UploadStatusWebSocketHandler uploadStatusWebSocketHandler) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.flag = flag;
        this.modelMessageService = modelMessageService;
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
            log.info("远程目录存在");

            //上传模型数据
            uploadDirectoryRecursive(sftpChannel, new File(localDir), remoteDir);

            log.info("上传到nfs服务器成功");

            // 更新数据库
            log.info("准备更新数据库，模型id为: {}", modelId);
            ModelMessage modelMessage = modelMessageService.getById(modelId);

            // 获取到上传文件的路径
            // 获取远程目录中的文件和文件夹
            log.info("准备列出远程目录内容: {}", remoteDir);
            if (!sftpChannel.isConnected()) {
                log.error("SFTP连接已断开，无法执行ls操作");
            }
            Vector<ChannelSftp.LsEntry> files = sftpChannel.ls(remoteDir);
            log.info("远程目录 {} 下的文件数量: {}", remoteDir, files.size());
            if (files.size() == 0) {
                log.error("远程目录为空，无法获取第一个文件");
                throw new RuntimeException("远程目录为空");
            }
            String firstFileName = files.get(0).getFilename();
            log.info("远程目录第一个文件/文件夹名称: {}", firstFileName);
            if (firstFileName.equals(".") || firstFileName.equals("..")) {
                // 跳过 . 和 ..，取第一个实际文件
                firstFileName = files.get(1).getFilename();
                log.info("跳过 . 或 ..，使用第二个文件: {}", firstFileName);
            }
            String remoteModelPath = remoteDir + "/" + files.get(0).getFilename();
            log.info("远程模型路径:"+ remoteModelPath );
            modelMessage.setAllDataAddress(remoteModelPath);
            processFilesInRemoteDirectory(sftpChannel, modelMessage, remoteModelPath);
            modelMessage.setUpdateTime(LocalDateTime.now());
            modelMessage.setStatus(1);
            modelMessageService.updateById(modelMessage);

            //上传完毕后，删除本地目录
            deleteLocalDirectory(new File(localDir));

            // 当上传成功之后将信息通过websocket回弹到前端
            uploadStatusWebSocketHandler.sendUploadStatus(String.valueOf(userId), "上传完成!" + String.valueOf(modelMessage.getId()));

        }catch (Exception e){
            log.error("上传失败，错误信息：{}", e.getMessage(), e);
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

    public void shRemoteScript(Session sshSession, String scriptPath) throws JSchException, IOException {
        // 执行脚本
        System.out.println("镜像脚本开始执行！！！");

        // 请求伪终端
        sshSession.setConfig("RequestPTY", "true");

        // 创建 exec channel 来执行命令
        ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");

        // 拼接命令
        String command = "echo '" + password + "' | sudo -S sh " + scriptPath;
        channelExec.setCommand(command);

        // 设置标准错误输出流
        InputStream errorStream = channelExec.getErrStream();
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));

        // 设置标准输出流
        InputStream inputStream = channelExec.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        // 连接并开始执行命令
        channelExec.connect();

        // 使用多线程分别读取输出流和错误流
        Thread stdoutThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("stdout: " + line);  // 打印标准输出
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread stderrThread = new Thread(() -> {
            try {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    System.out.println("stderr: " + line);  // 打印标准错误输出
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // 启动读取线程
        stdoutThread.start();
        stderrThread.start();

        // 等待命令执行完成
        while (!channelExec.isClosed()) {
            try {
                Thread.sleep(100);  // 等待命令执行完成
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 断开连接
        channelExec.disconnect();
        System.out.println("镜像脚本执行完成！！！");
    }
}
