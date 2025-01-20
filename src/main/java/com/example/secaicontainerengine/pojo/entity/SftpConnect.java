package com.example.secaicontainerengine.pojo.entity;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import lombok.Data;

@Data
public class SftpConnect {
    private Session session;
    private ChannelSftp sftpChannel;
}
