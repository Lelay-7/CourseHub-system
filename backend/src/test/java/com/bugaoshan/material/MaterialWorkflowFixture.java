package com.bugaoshan.material;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.bugaoshan.mapper.*;
import com.bugaoshan.service.MaterialService;
import com.bugaoshan.service.MaterialReviewService;
import com.bugaoshan.service.impl.MaterialServiceImpl;
import com.bugaoshan.storage.LocalFileStorageService;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import javax.sql.DataSource;
import java.nio.file.Path;

public class MaterialWorkflowFixture {
    public final JdbcTemplate db;
    public final MaterialMapper materials;
    public final MaterialHistoryMapper history;
    public final MaterialService service;
    public final MaterialReviewService review;
    public final LocalFileStorageService files;

    public MaterialWorkflowFixture(DataSource source, Path directory, boolean initialize) throws Exception {
        db=new JdbcTemplate(source);
        if(initialize) new ResourceDatabasePopulator(new ClassPathResource("db/material-review-fixture.sql")).execute(source);
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(source);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath:mapper/*Mapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        materials=session.getMapper(MaterialMapper.class); history=session.getMapper(MaterialHistoryMapper.class);
        files=new LocalFileStorageService(directory.toString());
        var users=session.getMapper(UserMapper.class); var scores=session.getMapper(ScoreRecordMapper.class);
        var tx=new DataSourceTransactionManager(source);
        var ownerProxy=new ProxyFactory(new MaterialServiceImpl(materials,session.getMapper(FavoriteMapper.class),users,scores,
                session.getMapper(MaterialDownloadMapper.class),session.getMapper(MaterialReportMapper.class),files,history));
        ownerProxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        service=(MaterialService) ownerProxy.getProxy();
        var adminProxy=new ProxyFactory(new MaterialReviewService(materials,history,users,scores,files));
        adminProxy.setProxyTargetClass(true);
        adminProxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        review=(MaterialReviewService)adminProxy.getProxy();
        db.update("INSERT INTO user(user_id,username,nickname,user_type,status,score_balance,total_score) VALUES(1,'owner','上传者甲',1,1,20,20),(2,'reader','同学乙',1,1,20,20),(3,'admin','管理员',3,1,0,0),(4,'admin2','管理员乙',3,1,0,0)");
        db.update("INSERT INTO course VALUES(1,'课程',1,0)");
    }
}
