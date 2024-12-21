package com.example.secaicontainerengine.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@MapperScan("com.example.secaicontainerengine.mapper")
public class MyBatisPlusConfig {

    /**
     * 拦截器配置
     * 下面的是分页的插件：在执行 MyBatis 操作时，如果涉及分页，这个拦截器会自动介入，修改 SQL 语句，加入分页的 SQL 片段
     *
     * @return
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

//    /**
//     * 将SqlSessionFactory配置为MybatisPlus的MybatisSqlSessionFactoryBean
//     *
//     * @param dataSource 默认配置文件中的数据源
//     * @return MybatisSqlSessionFactoryBean
//     */
//    @Bean("mpSqlSessionFactory")
//    public MybatisSqlSessionFactoryBean setSqlSessionFactory(DataSource dataSource) {
//        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
//        // 设置数据源
//        bean.setDataSource(dataSource);
//        // 简化PO的引用
//        bean.setTypeAliasesPackage("com.example.secaicontainerengine.pojo.entity");
//        // 设置全局配置
//        bean.setGlobalConfig(this.globalConfig());
//        return bean;
//    }
//
//    /**
//     * 设置全局配置
//     *
//     * @return 全局配置
//     */
//    public GlobalConfig globalConfig() {
//        GlobalConfig globalConfig = new GlobalConfig();
//        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
//        // 设置逻辑删除字段
//        dbConfig.setLogicDeleteField("deleted");
//        // 设置更新策略
//        dbConfig.setUpdateStrategy(FieldStrategy.NOT_EMPTY);
//        globalConfig.setDbConfig(dbConfig);
//        return globalConfig;
//    }
}
