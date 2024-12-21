package com.example.secaicontainerengine.constant;

import java.io.File;

public interface FileConstant {

//    String FILE_BASE_PATH = "src/main/resources/";

    // 动态获取项目根路径并指向 src/main/resources
    String FILE_BASE_PATH = System.getProperty("user.dir") + File.separator + "src" + File.separator + "main" + File.separator + "resources";
    long FILE_MAX_SIZE = 1024L * 1024L * 1024L * 2L; //2G
}
