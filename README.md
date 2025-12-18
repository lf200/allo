# 1.依赖软件安装
* IDEA专业版
* JDK=21
* MySQL>=5.7
* Maven>=3.0（需要换成阿里源，提高依赖下载速度）
> 具体步骤参考飞书文档：https://r0cha4lx2kh.feishu.cn/docx/TxDpdDUUgoDTAQxxpnEcRlLHnMh?from=from_copylink
# 2.启动项目后可能遇到的问题
## 2.1 k8s报错
`/resources/k8s/config`文件不是最新的集群配置文件，需要从主节点拷贝最新的配置文件替换
## 2.2 数据库报错
检查`application.yaml`文件中配置的数据库地址、用户名及密码是否正确
* 相关配置位于spring.datasource选项下
## 2.3 检查配置文件`localhost`配置项
需要把`logUrl`和`resultUrl`修改为自己启动的服务的ip

## 评测代码的地址是通过 NFS 挂载实现的，修改评测实际使用的脚本路径：
    在FileUtils.java的generateEvaluateRunSh方法中硬编码：
    301行："python3 /app/systemData/evaluation_code/art/eva_start.py &\n" +
    在 Kubernetes 的 Pod 配置中，通过 volumeMounts 将 NFS 上的 systemData 目录挂载到容器内的 /app/systemData
    在 attack_pod_gpu.yml 模板文件中定义了 volumeMounts 和 volumes
    把art替换为secai-common: 在FileUtils.java中替换

## 3. 使用环境变量覆盖敏感配置并打包
`src/main/resources/application.yml` 已支持读取环境变量，避免在 Jar 中写死 IP/账号：

* 数据库：`DATASOURCE_URL`、`DATASOURCE_USERNAME`、`DATASOURCE_PASSWORD`
* SFTP/NFS：`SFTP_HOST`、`SFTP_USERNAME`、`SFTP_PASSWORD`、`NFS_ROOT_PATH` 等（完整示例见 `config/example.env`）
* Registry/回调地址：`DOCKER_REGISTRY_HOST`、`LOCAL_LOG_URL`、`LOCAL_RESULT_URL`
* K8s GPU 资源默认值：`K8S_GPU_MEMORY`、`K8S_GPU_CORE`、`K8S_GPU_NUM`

快速加载示例配置（修改 `.env` 后导入环境即可运行打包好的 Jar，无需再次改代码）：

```bash
cp config/example.env .env               # 按目标环境改值
export $(grep -v '^#' .env | xargs)      # 导出环境变量
mvn clean package -DskipTests            # 打包生成 Jar
java -jar target/secAI-container-engine-0.0.1-SNAPSHOT.jar
```

> 只需按目标环境修改 `.env`（或直接设置对应环境变量），导入后即可运行打包好的 Jar；部署到 K8s/Systemd 等场景时，也可以把这些键值写入 Secret/ConfigMap 或服务的环境变量配置中，保持镜像与配置解耦。

部署到 K8s 时也可以将上述变量写入 Secret/ConfigMap，再通过 `env` 传入 Deployment/Pod，做到镜像与配置解耦。