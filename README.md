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