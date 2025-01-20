package com.example.secaicontainerengine.util;

import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {
    // 解压文件
    public static String unzipFile(String zipFilePath, String extractDirPath) throws IOException {
        // 创建解压目录（如果不存在）
        File extractDir = new File(extractDirPath);
        if (!extractDir.exists() && !extractDir.mkdirs()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法创建解压目录");
        }

        String topLevelDir = null; // 用于记录顶级目录


        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {

                // 如果顶级目录为空，记录第一级目录名称
                if (topLevelDir == null) {
                    topLevelDir = getTopLevelDirectory(zipEntry.getName());
                }
                File newFile = new File(extractDirPath, zipEntry.getName());

                // 检查是否有路径穿越漏洞（防止解压到非预期路径）
                if (!newFile.getCanonicalPath().startsWith(new File(extractDirPath).getCanonicalPath())) {
                    throw new IOException("解压文件路径安全性检查失败: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    // 创建目录
                    if (!newFile.mkdirs()) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法创建目录: " + newFile.getPath());
                    }
                } else {
                    // 确保父目录存在
                    File parentDir = newFile.getParentFile();
                    if (!parentDir.exists() && !parentDir.mkdirs()) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法创建父目录: " + parentDir.getPath());
                    }

                    // 写入文件
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }

                }
                zis.closeEntry();
            }
        }

        // 删除压缩包文件
        File zipFile = new File(zipFilePath);
        if (!zipFile.delete()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解压成功，但删除压缩包失败: " + zipFilePath);
        }

        // 返回解压后的顶级目录绝对路径
        if (topLevelDir != null) {
            return new File(extractDirPath, topLevelDir).getAbsolutePath();
        } else {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解压失败，没有找到顶级目录");
        }
    }

    // 工具方法：提取顶级目录
    private static String getTopLevelDirectory(String entryName) {
        int firstSlashIndex = entryName.indexOf('/');
        if (firstSlashIndex != -1) {
            return entryName.substring(0, firstSlashIndex); // 获取第一个斜杠前的内容
        }
        return null; // 如果没有 "/"，则说明没有目录结构
    }

    /**
     * 遍历指定路径下的所有文件和文件夹，并将它们的绝对路径存入到 ModelMessage 中
     *
     * @param rootPath 需要遍历的路径
     * @return ModelMessage 包含各类文件的路径信息
     */
    public static ModelMessage processFilesInDirectory(ModelMessage modelMessage, String rootPath) {


        // 根目录
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException("路径不存在或不是一个目录: " + rootPath);
        }

        // 遍历文件夹
        File[] files = rootDir.listFiles();
        if (files != null) {
            for (File file : files) {
                // 获取文件名和路径
                String absolutePath = file.getAbsolutePath();
                String fileName = file.getName().toLowerCase();

                // 根据文件类型判断并存储路径
                if (file.isDirectory()) {
                    // 判断一级目录名称
                    if ("model".equals(fileName)) {
                        modelMessage.setModelAddress(absolutePath);
                    } else if ("dataset".equals(fileName)) {
                        modelMessage.setDatasetAddress(absolutePath);
                    }
                } else {
                    // 判断文件名
                    if (fileName.equalsIgnoreCase("requirements.txt")) {
                        modelMessage.setEnvironmentAddress(absolutePath);
                    } else if (fileName.endsWith(".pth") || fileName.endsWith(".bin")) {
                        modelMessage.setWeightAddress(absolutePath);
                    } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                        modelMessage.setParameterAddress(absolutePath);
                    }
                }
            }
        }

        return modelMessage;
    }

    public static ModelMessage processFilesInRemoteDirectory(ChannelSftp sftpChannel, ModelMessage modelMessage, String remotePath) throws SftpException {
        Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(remotePath);

        for (ChannelSftp.LsEntry entry : entries) {
            String fileName = entry.getFilename().toLowerCase();
            String absolutePath = remotePath + "/" + entry.getFilename();

            if (entry.getAttrs().isDir()) {
                // 判断一级目录名称
                if ("model".equals(fileName)) {
                    modelMessage.setModelAddress(absolutePath);
                } else if ("dataset".equals(fileName)) {
                    modelMessage.setDatasetAddress(absolutePath);
                }
            } else {
                // 判断文件名
                if (fileName.equalsIgnoreCase("requirements.txt")) {
                    modelMessage.setEnvironmentAddress(absolutePath);
                } else if (fileName.endsWith(".pth") || fileName.endsWith(".bin")) {
                    modelMessage.setWeightAddress(absolutePath);
                } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                    modelMessage.setParameterAddress(absolutePath);
                }
            }
        }

        return modelMessage;
    }
}

