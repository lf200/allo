package com.example.secaicontainerengine.util;

import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.pojo.dto.model.BusinessConfig;
import com.example.secaicontainerengine.pojo.dto.model.Evaluation;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationConfig;
import com.example.secaicontainerengine.pojo.dto.model.ShowBusinessConfig;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
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
    //     * @return ModelMessage 包含各类文件的路径信息
     @return conda_env 用户上传的环境的压缩包名称
     */
    public static String processFilesInDirectory(ModelMessage modelMessage, String rootPath) {


        String condaEnv = null;

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
//                    if (fileName.equalsIgnoreCase("requirements.txt")) {
//                        modelMessage.setEnvironmentAddress(absolutePath);
//                    } else if (fileName.endsWith(".pth") || fileName.endsWith(".bin")) {
//                        modelMessage.setWeightAddress(absolutePath);
//                    } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
//                        modelMessage.setParameterAddress(absolutePath);
//                    }
                    if (fileName.endsWith(".tar.gz")) {
                        condaEnv = fileName.substring(0, fileName.lastIndexOf(".tar.gz"));
                        modelMessage.setEnvironmentAddress(absolutePath);
                    } else if (fileName.endsWith(".pth") || fileName.endsWith(".bin")) {
                        modelMessage.setWeightAddress(absolutePath);
                    } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                        modelMessage.setParameterAddress(absolutePath);
                    }
                }
            }
        }

        return condaEnv;
    }

    public static ModelMessage processFilesInRemoteDirectory(ChannelSftp sftpChannel, ModelMessage modelMessage, String remotePath) throws SftpException {
        Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(remotePath);
        log.info("开始处理远程路径: {}", remotePath);
        for (ChannelSftp.LsEntry entry : entries) {
            String fileName = entry.getFilename().toLowerCase();
            String absolutePath = remotePath + "/" + entry.getFilename();

            if (entry.getAttrs().isDir()) {
                // 判断一级目录名称
                if ("model".equals(fileName)) {
                    modelMessage.setModelAddress(absolutePath);
                } else if ("data".equals(fileName)) {
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

    // 生成模型运行脚本文件
    public static void generateRunSh(String condaEnv, Path runShPath, String modelFileName) throws IOException {


        // 原始脚本模板（使用变量替换环境名）
        String scriptContent = "#!/bin/bash\n\n" +
                "# 确保脚本在执行时不会中途退出\n" +
                "set -e\n\n" +
                "# 1. 初始化 Conda 环境\n" +
                "echo \"Initializing conda...\"\n" +
                "conda init bash\n" +
                "#source ~/.bashrc  # 确保 conda 初始化完成\n\n" +
                "# 2. 激活指定的 Conda 环境\n" +
                "echo \"Activating conda environment: " + condaEnv + "\"\n" +
                "source activate " + condaEnv + "\n\n" +
                "# 3. 运行模型测试代码(这里用模型代码代替)\n" +
                "echo \"Running" + modelFileName +  "...\"\n" +
//                "echo \"Running cifar10_detect_train.py...\"\n" +
                "export CRYPTOGRAPHY_OPENSSL_NO_LEGACY=1\n" +
                "python3 /app/userData/modelData/model/" + modelFileName + "\n\n" +
//                "python3 /app/userData/modelData/model/cifar10_detect_train.py\n\n" +
                "# 4. 更新数据库\n" +
                "echo \"Running update_table.py...\"\n" +
                "python3 /app/systemData/database_code/update_table.py\n\n" +
                "echo \"All scripts executed successfully!\"\n";

        try {
            Files.write(runShPath, scriptContent.getBytes());
            log.info("模型评测脚本生成成功！！");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 生成模型评测的脚本文件
    public static void generateEvaluateRunSh(String condaEnv, Path runShPath) throws IOException {


        // 原始脚本模板（使用变量替换环境名）
//        String scriptContent = "#!/bin/bash\n\n" +
//                "# 确保脚本在执行时不会中途退出\n" +
//                "set -e\n\n" +
//                "# 1. 初始化 Conda 环境\n" +
//                "echo \"Initializing conda...\"\n" +
//                "conda init bash\n" +
//                "#source ~/.bashrc  # 确保 conda 初始化完成\n\n" +
//                "# 2. 激活指定的 Conda 环境\n" +
//                "echo \"Activating conda environment: " + condaEnv + "\"\n" +
//                "source activate " + condaEnv + "\n\n" +
//                "# 3. 运行模型评测代码并更新数据库\n" +
//                "echo \"Running  模型评测...\"\n" +
//                "export CRYPTOGRAPHY_OPENSSL_NO_LEGACY=1\n" +
//                "python3 /app/systemData/evaluation_code/art/eva_start.py\n\n" +
//                "echo \"All scripts executed successfully!\"\n";
        String scriptContent = "#!/bin/bash\n\n" +
                "# 确保脚本在执行时不会中途退出\n" +
                "set -e\n\n" +
                "# 定义临时文件路径（使用固定路径避免权限问题）\n" +
                "TMP_MEM_FILE=\"/tmp/max_mem.tmp\"\n" +
//                "TMP_GPU_FILE=\"/tmp/max_gpu.tmp\"\n\n" +
//                "# 初始化临时文件（避免残留数据）\n" +
                "echo 0 > \"$TMP_MEM_FILE\"\n" +
                "echo 0 > \"$TMP_GPU_FILE\"\n\n" +
                "# 定义监控函数（后台运行，数据写入临时文件）\n" +
                "monitor_resources() {\n" +
                "  echo \"监控进程启动，PID: $$\" >&2\n" +
                "  while true; do\n" +
                "    # 获取当前进程组ID（PGID）\n" +
                "    pgid=$(ps -o pgid= -p $$ | tail -1)\n" +
                "    if [ -z \"$pgid\" ]; then \n" +
                "      echo \"无法获取进程组ID，退出监控\" >&2\n" +
                "      break; \n" +
                "    fi\n\n" +
                "    # 计算内存占用\n" +
                "    total_rss=0\n" +
                "    for pid in $(pgrep -g $pgid); do\n" +
                "      rss=$(ps -o rss= -p $pid 2>/dev/null)\n" +
                "      [ -n \"$rss\" ] && total_rss=$((total_rss + rss))\n" +
                "    done\n" +
                "    current_mem_mb=$((total_rss / 1024))\n\n" +
                "    # 写入内存最大值到临时文件（仅当更大时更新）\n" +
                "    if [ $current_mem_mb -gt $(cat \"$TMP_MEM_FILE\") ]; then\n" +
                "      echo \"$current_mem_mb\" > \"$TMP_MEM_FILE\"\n" +
                "      echo \"[$(date '+%H:%M:%S')] 当前最大内存: ${current_mem_mb} MB\" >&2\n" +
                "    fi\n\n" +
                "    # 计算显存占用（写入临时文件）\n" +
                "    if command -v nvidia-smi &> /dev/null; then\n" +
                "      current_gpu_mem=$(nvidia-smi --query-gpu=memory.used --format=csv,noheader,nounits 2>/dev/null || echo 0)\n" +
                "      if [ $current_gpu_mem -gt $(cat \"$TMP_GPU_FILE\") ]; then\n" +
                "        echo \"$current_gpu_mem\" > \"$TMP_GPU_FILE\"\n" +
                "        echo \"[$(date '+%H:%M:%S')] 当前最大显存: ${current_gpu_mem} MiB\" >&2\n" +
                "      fi\n" +
                "    fi\n\n" +
                "    sleep 1\n" +
                "  done\n" +
                "  echo \"监控进程退出\" >&2\n" +
                "}\n\n" +
                "# 启动监控后台进程\n" +
                "echo \"启动监控进程...\"\n" +
                "monitor_resources &\n" +
                "monitor_pid=$!\n\n" +
                "# 记录主进程ID（$$）和监控进程ID\n" +
                "main_pid=$$\n" +
                "echo \"主进程PID: $main_pid, 监控进程PID: $monitor_pid\" >&2\n\n" +
                "# 运行模型评测（原逻辑不变）\n" +
                "echo \"Initializing conda...\"\n" +
                "conda init bash\n" +
                "source ~/.bashrc\n" +
                "echo \"Activating conda environment: " + condaEnv + "\"\n" +
                "source activate " + condaEnv + "\n\n" +
                "echo \"Running 模型评测...\"\n" +
                "export CRYPTOGRAPHY_OPENSSL_NO_LEGACY=1\n" +
                "python3 /app/systemData/evaluation_code/secai-common/eva_start.py &\n" +
                "python_pid=$!  # 获取Python进程PID\n\n" +
                "# 等待Python进程完成（精准等待，避免阻塞）\n" +
                "wait $python_pid  # 仅等待Python进程\n" +
                "echo \"Python进程已完成，PID: $python_pid\" >&2\n\n" +
                "# 终止监控进程（原逻辑不变）\n" +
                "echo \"尝试终止监控进程...\"\n" +
                "kill -s SIGTERM $monitor_pid 2>/dev/null\n" +
                "sleep 2  # 等待监控进程响应\n" +
                "if kill -0 $monitor_pid 2>/dev/null; then\n" +
                "  kill -s SIGKILL $monitor_pid 2>/dev/null\n" +
                "  echo \"强制终止监控进程\" >&2\n" +
                "else\n" +
                "  echo \"监控进程已终止\" >&2\n" +
                "fi\n\n" +
                "# 从临时文件读取最大值（主shell获取数据）\n" +
                "max_mem=$(cat \"$TMP_MEM_FILE\")\n" +
                "max_gpu_mem=$(cat \"$TMP_GPU_FILE\")\n\n" +
                "# 输出结果（修复0值问题）\n" +
                "echo \"======================================\"\n" +
                "echo \"评测任务最大内存占用:${max_mem} MB\"\n" +
                "echo \"评测任务最大显存占用:${max_gpu_mem} MiB\"\n" +
                "echo \"======================================\"\n\n" +
                "# 清理临时文件（可选）\n" +
                "rm -f \"$TMP_MEM_FILE\" \"$TMP_GPU_FILE\"\n" +
                "echo \"All scripts executed successfully!\"";

        try {
            Files.write(runShPath, scriptContent.getBytes());
            log.info("模型评测脚本生成成功！！");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void generateImageSh(ModelMessage modelMessage, Path shDestinationPath, String condaEnv, String registryHost){

        Long userId = modelMessage.getUserId();
        Long modelId = modelMessage.getId();
        String buildAndPushScript = "#!/bin/bash\n" +
                "cd /home/xd-1/k8s/userData/" + userId + "/" + modelId + "/modelData\n" +
                "echo \"正在制作镜像...\"\n" +
                "sudo docker build --build-arg condaEnv=" + condaEnv + " -t " + modelId + ":latest .\n" +
                "echo \"正在给镜像打标签...\"\n" +
                "sudo docker tag " + modelId + ":latest " + registryHost + "/" + modelId + "\n" +
                "echo \"正在推送镜像到仓库...\"\n" +
                "sudo docker push " + registryHost + "/" + modelId + "\n";
        try {
            Files.write(shDestinationPath, buildAndPushScript.getBytes());
            log.info("镜像相关脚本生成成功！！");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 将目录的最后一级修改为 modelData
    public static String renameModelData(String modelSavePath) throws IOException {
        // 获取解压后的目录的最后一级并修改为 modelData
        File modelSaveDir = new File(modelSavePath);
        if (modelSaveDir.exists() && modelSaveDir.isDirectory()) {
            String parentDir = modelSaveDir.getParent();  // 获取父目录
            String newDirName = "modelData";  // 新的目录名称
            File renamedDir = new File(parentDir, newDirName);

            // 重命名目录
            if (!modelSaveDir.renameTo(renamedDir)) {
                throw new IOException("无法重命名目录: " + modelSavePath);
            }
            modelSavePath = renamedDir.getAbsolutePath();  // 更新为重命名后的路径
            log.info("解压后的目录已重命名为: " + modelSavePath);
            return modelSavePath;
        } else {
            throw new IOException("指定的解压目录无效: " + modelSavePath);
        }
    }

    // 返回指定路径下的第一个py结尾的文件名（为了获取到用户上传的文件中的模型运行文件名称，方便后续使用）
    public static String findFirstPyFileName(String basePath) {
        // 1. 校验路径有效性
        Path targetDir = Paths.get(basePath);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return null; // 路径不存在或不是目录
        }

        // 2. 遍历目标路径下的直接子文件（非递归）
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (Path file : stream) {
                // 检查是否是普通文件且后缀为 .py
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".py")) {
                    return file.getFileName().toString(); // 返回文件名（如 "test.py"）
                }
            }
        } catch (IOException e) {
            // 处理目录访问异常（如权限不足）
            e.printStackTrace();
        }

        return null; // 未找到 .py 文件
    }

//    public static void generateEvaluationYamlConfigs(String outputPath, EvaluationConfig config) throws IOException {
//        List<String> evaluateMethods = config.getEvaluateMethods();
//        if (evaluateMethods == null || evaluateMethods.isEmpty()) {
//            throw new IllegalArgumentException("evaluateMethods 不可为空");
//        }
//
//        // 基础模型配置（公共部分）
//        Map<String, Object> modelInstantiation = new HashMap<>();
//        modelInstantiation.put("model_path", "/app/userData/modelData/model/" + config.getModelNetFileName());
//        modelInstantiation.put("weight_path", "/app/userData/modelData/" + config.getWeightFileName());
//        modelInstantiation.put("model_name", config.getModelNetName());
//        modelInstantiation.put("parameters", new HashMap<>()); // 空参数
//
//        Map<String, Object> modelEstimator = new HashMap<>();
//        modelEstimator.put("framework", config.getFramework());
//        modelEstimator.put("task", config.getTask());
//        Map<String, Object> estimatorParams = new HashMap<>();
//        estimatorParams.put("input_shape", Arrays.asList(3, 32, 32));
//        estimatorParams.put("nb_classes", 10);
//        estimatorParams.put("clip_values", Arrays.asList(0, 1));
//        estimatorParams.put("device", "cuda");
//        estimatorParams.put("device_type", "gpu");
//        modelEstimator.put("parameters", estimatorParams);
//        // 合并 model 的两个子节点（instantiation 和 estimator）
//        Map<String, Object> modelConfig = new HashMap<>();
//        modelConfig.put("instantiation", modelInstantiation);
//        modelConfig.put("estimator", modelEstimator); // estimator 作为 model 的直接子节点
//
//        // 遍历生成每个攻击方法的配置文件
//        for (String method : evaluateMethods) {
//            // 构造完整配置
//            Map<String, Object> root = new HashMap<>();
//            root.put("model", modelConfig); // 键无引号
//
//            Map<String, Object> attackConfig = new HashMap<>();
//            attackConfig.put("method", method);
//            Map<String, Object> attackParams = new HashMap<>();
//            attackParams.put("eps", 0.4); // 固定参数，可扩展
//            attackConfig.put("parameters", attackParams);
//            root.put("attack", attackConfig);
//
//            // 生成文件名（如：adversarialAttack.yaml）
//            String fileName = method + ".yaml";
//            File outputFile = new File(outputPath, fileName);
//
//            // 创建父目录（如果不存在）
//            if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
//                throw new IOException("无法创建输出目录：" + outputPath);
//            }
//
//            // 生成 YAML 内容（仅主体数据）
//            try (FileWriter writer = new FileWriter(outputFile)) {
//                DumperOptions dumperOptions = new DumperOptions();
//                dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // 块格式（YAML 标准格式）
//                Yaml yaml = new Yaml(dumperOptions);
//                yaml.dump(root, writer); // 仅写入主体配置数据
//            }
//        }
//    }

    public static void generateEvaluationYamlConfigs(EvaluationConfig evaluationConfig, BusinessConfig businessConfig, String outputPath) throws IOException {
        // 构造 model 节点
        Map<String, Object> modelInstantiation = new HashMap<>();
        modelInstantiation.put("model_path", "/app/userData/modelData/model/" + evaluationConfig.getModelNetFileName());
        modelInstantiation.put("weight_path", "/app/userData/modelData/" + evaluationConfig.getWeightFileName());
        modelInstantiation.put("model_name", evaluationConfig.getModelNetName());
        modelInstantiation.put("parameters", new HashMap<>()); // 空参数

        Map<String, Object> modelEstimator = new HashMap<>();
        modelEstimator.put("framework", evaluationConfig.getFramework());
        modelEstimator.put("task", evaluationConfig.getTask());

        Map<String, Object> estimatorParams = new HashMap<>();
//        estimatorParams.put("input_shape", Arrays.asList(3, 32, 32));
//        estimatorParams.put("nb_classes", 10);
        // 输入数据形状：优先使用传入的参数，如果为空则使用默认值 [3, 32, 32]
        List<Integer> inputShape;
        if (evaluationConfig.getInputChannels() != null
                && evaluationConfig.getInputHeight() != null
                && evaluationConfig.getInputWidth() != null) {
            inputShape = Arrays.asList(
                    evaluationConfig.getInputChannels(),
                    evaluationConfig.getInputHeight(),
                    evaluationConfig.getInputWidth()
            );
        } else {
            // 向后兼容：如果参数为空，使用默认值
            inputShape = Arrays.asList(3, 32, 32);
        }
        estimatorParams.put("input_shape", inputShape);

        // 类别数目：优先使用传入的参数，如果为空则使用默认值 10
        Integer nbClasses = evaluationConfig.getNbClasses() != null
                ? evaluationConfig.getNbClasses()
                : 10; // 向后兼容：默认值
        estimatorParams.put("nb_classes", nbClasses);
        estimatorParams.put("clip_values", Arrays.asList(0, 1));
        estimatorParams.put("device", "cuda");
        estimatorParams.put("device_type", "gpu");
        modelEstimator.put("parameters", estimatorParams);

        Map<String, Object> modelConfig = new HashMap<>();
        modelConfig.put("instantiation", modelInstantiation);
        modelConfig.put("estimator", modelEstimator);

        // 构造 evaluation 节点
        Map<String, Object> evaluation = new LinkedHashMap<>();

        // 初始化每个维度为空 Map
        List<String> dimensions = Arrays.asList("basic", "robustness", "interpretability", "safety", "generalization", "fairness");
        for (String dim : dimensions) {
            evaluation.put(dim, new LinkedHashMap<>());
        }

        for (BusinessConfig.EvaluationDimensionConfig dimensionConfig : businessConfig.getEvaluateMethods()) {
            String dimension = dimensionConfig.getDimension();
            List<BusinessConfig.MethodMetricPair> methodMetricPairs = dimensionConfig.getMethodMetricMap();
            if (!dimensions.contains(dimension)) {
                continue; // 忽略未定义的维度
            }
            Map<String, List<String>> methodMap = (Map<String, List<String>>) evaluation.get(dimension);
            for (BusinessConfig.MethodMetricPair pair : methodMetricPairs) {
                String method = pair.getMethod();
                List<String> metrics = pair.getMetrics() != null ? pair.getMetrics() : new ArrayList<>();
                methodMap.put(method, metrics);
            }
        }

        // 合并最终结构
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", modelConfig);
        root.put("evaluation", evaluation);

        // 输出 evaluationConfig.yaml
        File outputFile = new File(outputPath, "evaluationConfig.yaml");
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            throw new IOException("无法创建输出目录：" + outputPath);
        }

        try (FileWriter writer = new FileWriter(outputFile)) {
            DumperOptions dumperOptions = new DumperOptions();
            dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(dumperOptions);
            yaml.dump(root, writer);
        }
    }
}

