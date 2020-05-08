package com.github.hcsp;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.engine.User;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MyBatisCrawlerDao implements CrawlerDao {
    private SqlSessionFactory sqlSessionFactory;

    public MyBatisCrawlerDao() {
        try {
            // 指定全局配置文件
            String resource = "db/mybatis/config.xml";
            // 读取配置文件
            InputStream inputStream = Resources.getResourceAsStream(resource);
            // 构建sqlSessionFactory
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public String getNextLinkThenDelete() throws SQLException {
        // 获取sqlSession
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            String url = sqlSession.selectOne("com.github.hcsp.MyMapper.selectNextAvailableLink");
            if (url != null) {
                sqlSession.selectOne("com.github.hcsp.MyMapper.deleteLink", url);
            }
            return url;
        }
    }

    @Override
    public void insertNewsIntoDatabase(String url, String title, String content) throws SQLException {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            sqlSession.insert("com.github.hcsp.MyMapper.insertNews", new News(url, title, content));
        }
    }

    @Override
    public boolean isLinkProcessed(String link) throws SQLException {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            int count = (Integer) sqlSession.selectOne("com.github.hcsp.MyMapper.countLink", link);
            return count != 0;
        }
    }

    @Override
    public void insertProcessesLink(String link) {
        Map<String, Object> param = new HashMap<>();
        param.put("tableName", "links_already_processed");
        param.put("link", link);
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            sqlSession.selectOne("com.github.hcsp.MyMapper.insertLink", param);
        }

    }

    @Override
    public void insertLinkToBeProcessed(String link) {
        Map<String, Object> param = new HashMap<>();
        param.put("tableName", "LINKS_TO_BE_PROCESSED");
        param.put("link", link);
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            sqlSession.selectOne("com.github.hcsp.MyMapper.insertLink", param);
        }
    }
}
