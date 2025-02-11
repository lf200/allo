-- MySQL dump 10.13  Distrib 8.0.41, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: SecAI_backend
-- ------------------------------------------------------
-- Server version	8.0.41-0ubuntu0.22.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `container`
--

DROP TABLE IF EXISTS `container`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `container` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `containerName` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '容器名称',
  `containerId` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '容器ID',
  `nameSpace` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容器命名空间',
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容器状态',
  `restarts` int DEFAULT NULL COMMENT '容器重启次数',
  `AGE` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容器存活时间',
  `nodeName` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所在节点名称',
  `imageId` bigint NOT NULL COMMENT '关联的镜像ID',
  `userId` bigint NOT NULL COMMENT '当前容器所属用户ID',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  `modelId` bigint NOT NULL COMMENT '该容器所属的模型id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1889252481668431874 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='容器';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `container`
--

LOCK TABLES `container` WRITE;
/*!40000 ALTER TABLE `container` DISABLE KEYS */;
INSERT INTO `container` VALUES (1889252481668431873,'nginx-pod','ac004c40-a566-4ca7-b991-1e45899673f8','default','Succeeded',0,'24',NULL,0,1,'2025-02-11 17:57:46','2025-02-11 17:58:10',0,31441);
/*!40000 ALTER TABLE `container` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `image`
--

DROP TABLE IF EXISTS `image`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `imageName` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '镜像名称',
  `url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '拉取地址',
  `size` bigint NOT NULL COMMENT '镜像大小',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='容器';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `image`
--

LOCK TABLES `image` WRITE;
/*!40000 ALTER TABLE `image` DISABLE KEYS */;
INSERT INTO `image` VALUES (1,'数据安全','nginx',1024,'2024-12-24 11:35:20','2024-12-24 11:35:20',0),(2,'模型安全','busybox',1024,'2024-12-24 11:35:20','2025-01-13 15:51:53',0),(3,'训练安全','redis',1024,'2024-12-24 11:35:20','2024-12-24 11:35:20',0),(4,'推理安全','tomcat',1024,'2024-12-24 11:35:20','2024-12-24 11:35:20',0),(5,'部署安全','hello-world',1024,'2024-12-24 11:35:20','2024-12-24 11:35:20',0);
/*!40000 ALTER TABLE `image` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `log`
--

DROP TABLE IF EXISTS `log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `log` (
  `logTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '该条日志的生成时间',
  `podName` varchar(30) DEFAULT NULL,
  `containerName` varchar(30) DEFAULT NULL,
  `namespace` varchar(30) DEFAULT NULL,
  `userId` int DEFAULT NULL,
  `messageKey` varchar(30) DEFAULT NULL,
  `messageValue` varchar(100) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=652140553 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='容器运行过程中产生的日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `log`
--

LOCK TABLES `log` WRITE;
/*!40000 ALTER TABLE `log` DISABLE KEYS */;
INSERT INTO `log` VALUES ('2025-02-10 20:59:05','12321','2313','6363',3444,'运行进度','95%',-878788607),('2025-02-10 21:08:23','12321','2313','6363',3444,'运行进度','15%',-786505726),('2025-02-10 21:06:21','12321','2313','6363',3444,'运行进度','45%',652140546),('2025-02-11 10:47:09','12321','2313','6363',3444,'运行进度','5%',652140547),('2025-02-11 11:19:27','34313','412','4242',6464,'运行进度','1%',652140548),('2025-02-11 11:29:02','34313','412','4242',6464,'运行进度','99%',652140549),('2025-02-11 11:32:35','34313','412','4242',6464,'运行进度','60%',652140550),('2025-02-11 11:34:15','34313','412','4242',6464,'运行进度','10%',652140551),('2025-02-11 17:08:04','34313','412','4242',6464,'运行进度','16%',652140552);
/*!40000 ALTER TABLE `log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `model_message`
--

DROP TABLE IF EXISTS `model_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `userId` bigint NOT NULL COMMENT '用户ID',
  `modelAddress` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型文件存放地址',
  `datasetAddress` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集存放地址',
  `environmentAddress` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '运行环境配置文件地址',
  `weightAddress` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权重文件存放地址',
  `parameterAddress` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型超参数地址',
  `modelConfig` text COLLATE utf8mb4_unicode_ci COMMENT '模型信息(json数组)',
  `resourceConfig` text COLLATE utf8mb4_unicode_ci COMMENT '资源信息(json数组)',
  `businessConfig` text COLLATE utf8mb4_unicode_ci COMMENT '业务信息(json数组)',
  `allDataAddress` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型所有数据的文件夹地址',
  `status` int DEFAULT '0' COMMENT '模型状态 0-待评测(未上传) 1-待评测(已上传) 2-评测中 3-成功 4-失败',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDeleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0: 正常, 1: 已删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1877638738337521666 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `model_message`
--

LOCK TABLES `model_message` WRITE;
/*!40000 ALTER TABLE `model_message` DISABLE KEYS */;
INSERT INTO `model_message` VALUES (1871443570794110978,1871442622277427202,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1871442622277427202/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1871442622277427202/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1871442622277427202/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1871442622277427202/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1871442622277427202/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1871442622277427202/test',0,'2024-12-24 14:31:31','2024-12-24 14:31:31',0),(1877538363743834114,1242343443,'/mnt/nfs/model_data/1242343443/test/model','/mnt/nfs/model_data/1242343443/test/dataset','/mnt/nfs/model_data/1242343443/test/requirements.txt','/mnt/nfs/model_data/1242343443/test/test.pth','/mnt/nfs/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/mnt/nfs/model_data/1242343443/test',0,'2025-01-10 10:10:03','2025-01-10 10:10:03',0),(1877544286239469569,1242343443,'/mnt/nfs/model_data/1242343443/test/model','/mnt/nfs/model_data/1242343443/test/dataset','/mnt/nfs/model_data/1242343443/test/requirements.txt','/mnt/nfs/model_data/1242343443/test/test.pth','/mnt/nfs/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/mnt/nfs/model_data/1242343443/test',0,'2025-01-10 10:33:35','2025-01-10 10:33:35',0),(1877555105102770178,1242343443,'/mnt/nfs/model_data/1242343443/test/model','/mnt/nfs/model_data/1242343443/test/dataset','/mnt/nfs/model_data/1242343443/test/requirements.txt','/mnt/nfs/model_data/1242343443/test/test.pth','/mnt/nfs/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/mnt/nfs/model_data/1242343443/test',0,'2025-01-10 11:16:35','2025-01-10 11:16:35',0),(1877558419244605441,1242343443,'/mnt/nfs/model_data/1242343443/test/model','/mnt/nfs/model_data/1242343443/test/dataset','/mnt/nfs/model_data/1242343443/test/requirements.txt','/mnt/nfs/model_data/1242343443/test/test.pth','/mnt/nfs/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/mnt/nfs/model_data/1242343443/test',0,'2025-01-10 11:29:45','2025-01-10 11:29:45',0),(1877558623842754561,1242343443,'/mnt/nfs/model_data/1242343443/test/model','/mnt/nfs/model_data/1242343443/test/dataset','/mnt/nfs/model_data/1242343443/test/requirements.txt','/mnt/nfs/model_data/1242343443/test/test.pth','/mnt/nfs/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/mnt/nfs/model_data/1242343443/test',0,'2025-01-10 11:30:33','2025-01-10 11:30:33',0),(1877558840998649858,1242343443,'/mnt/nfs/model_data/1242343443/test/model','/mnt/nfs/model_data/1242343443/test/dataset','/mnt/nfs/model_data/1242343443/test/requirements.txt','/mnt/nfs/model_data/1242343443/test/test.pth','/mnt/nfs/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/mnt/nfs/model_data/1242343443/test',0,'2025-01-10 11:31:25','2025-01-10 11:31:25',0),(1877611131625738241,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 14:59:12','2025-01-10 14:59:12',0),(1877613038519881729,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:06:47','2025-01-10 15:06:47',0),(1877613392313614337,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:08:11','2025-01-10 15:08:11',0),(1877613645074984961,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:09:12','2025-01-10 15:09:12',0),(1877613945487798273,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:10:23','2025-01-10 15:10:23',0),(1877616871857975298,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:22:01','2025-01-10 15:22:01',0),(1877621120419405826,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:38:54','2025-01-10 15:38:54',0),(1877622024501612545,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:42:29','2025-01-10 15:42:29',0),(1877626410640556033,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 15:59:55','2025-01-10 15:59:55',0),(1877627747612622850,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 16:05:14','2025-01-10 16:05:14',0),(1877628000730570754,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 16:06:15','2025-01-10 16:06:15',0),(1877628346236329986,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 16:07:37','2025-01-10 16:07:37',0),(1877638738337521665,1242343443,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/model','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/dataset','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/requirements.txt','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.pth','/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test/test.yaml',NULL,NULL,NULL,'/home/cfz/Code/IDEA/secAI-container-engine/src/main/resources/model_data/1242343443/test',0,'2025-01-10 16:48:54','2025-01-10 16:48:54',0);
/*!40000 ALTER TABLE `model_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `userAccount` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `userPassword` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `userName` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户昵称',
  `userAvatar` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像',
  `userProfile` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户简介',
  `userRole` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin/ban',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1871442622277427203 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1871442622277427202,'123456','bb2411666908778edf510d243f8b5d6a',NULL,NULL,NULL,'user','2024-12-24 14:27:45','2024-12-24 14:27:45',0);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-02-11 17:59:26
