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
