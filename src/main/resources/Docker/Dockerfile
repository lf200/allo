# 使用 Ubuntu 作为基础镜像
FROM ubuntu:22.04

# 定义构建参数（必须在此处声明才能被后续指令使用）
ARG condaEnv=ai_sec_test

# 安装依赖：curl 和 bzip2 用于安装 Miniconda
RUN apt-get update && \
    apt-get install -y wget curl bzip2 && \
    rm -rf /var/lib/apt/lists/*

# 安装 Miniconda
RUN echo "install miniconda"
RUN wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O miniconda.sh && \
    bash miniconda.sh -b -p /opt/conda && \
    rm -f miniconda.sh

# 设置 Conda 环境变量，确保 conda 可用
ENV PATH="/opt/conda/bin:$PATH"

# 将本地的 Conda 环境压缩包复制到容器中
COPY ${condaEnv}.tar.gz /opt/conda/envs/

# 解压 Conda 环境
RUN tar -xzvf /opt/conda/envs/${condaEnv}.tar.gz -C /opt/conda/envs/ && \
    rm /opt/conda/envs/${condaEnv}.tar.gz

#RUN conda run -n ai_sec_test conda install pip

# 安装 mysql-connector-python
#RUN conda run -n ai_sec_test pip install mysql-connector-python -i https://pypi.tuna.tsinghua.edu.cn/simple
RUN echo "install mysql-connector-python"
RUN conda run -n ${condaEnv} conda install pymysql

# 安装shap和sklearn
RUN echo "install shap==0.47.2 and scikit-learn==1.5.0"
RUN conda run -n ${condaEnv} pip install shap==0.47.2 scikit-learn==1.5.0 -i https://pypi.tuna.tsinghua.edu.cn/simple


# 设置工作目录
WORKDIR /app

# 默认命令
CMD ["bash", "-c", "sleep 86400"]
