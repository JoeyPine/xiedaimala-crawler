package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.h2.command.Prepared;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";

    @SuppressWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws IOException, SQLException {

        Connection connection = DriverManager.getConnection("jdbc:h2:file:/Users/Song/IdeaProjects/xiedaimala-crawler/news",
                USER_NAME, PASSWORD);

        while (true) {
            //每次循环开始就从数据库加载数据
            //待处理的链接池
            //从数据库加载即将处理的链接的池子
            List<String> linkPool = loadUrlFromDatabase(connection, "select link from LINKS_TO_BE_PROCESSED");

            //已处理的链接池
            //从数据库加载已经处理的链接的池子
            Set<String> processedlinks = new HashSet<>(loadUrlFromDatabase(connection,
                    "select link from LINKS_ALREADY_PROCESSED"));
            linkPool.add("https://sina.cn");

            if (linkPool.isEmpty()) {
                break;
            }

            //从待处理池子中捞一个来处理
            //处理完成后从池子（包括数据库）中删除
            String link = linkPool.remove(linkPool.size() - 1);
            //ArrayList从尾部删除更有效率
            //每次处理完，更新数据库
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM LINKS_TO_BE_PROCESSED WHERE LINK = ?")) {
                statement.setString(1, link);
                statement.executeUpdate();
            }

            //询问数据库，当前链接是不是已经被处理过了
            if (isLinkProcessed(connection, link)) {
                continue;
            }

            if (isInterestingLink(link)) {
                Document doc = httpGetAndParseHtml(link);
//                select方法返回的是Element，Element继承了ArrayList
//                ArrayList<Element> links = doc.select("a");

                //attr获取该属性值，添加到链接池
                parseUrlsFromPageAndStoreIntoDatabase(connection, linkPool, doc);

                //假如是一个新闻的详情页，就存入数据库，否则什么都不做
                storeIntoDatabaseIfItIsNewsPage(doc);

                insertLintIntoDatabase(connection, link, "INSERT INTO LINKS_ALREADY_PROCESSED (link) values (?)");

                processedlinks.add(link);
            }
        }
    }

    private static void parseUrlsFromPageAndStoreIntoDatabase(Connection connection, List<String> linkPool, Document doc) throws SQLException {
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");
            linkPool.add(href);
            insertLintIntoDatabase(connection, href, "INSERT INTO LINKS_TO_BE_PROCESSED (link) values (?)");
        }
    }

    private static boolean isLinkProcessed(Connection connection, String link) throws SQLException {
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement("select link from LINKS_ALREADY_PROCESSED where link = ?");) {
            statement.setString(1, link);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                return true;
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return false;
    }

    private static void insertLintIntoDatabase(Connection connection, String link, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }

    private static List<String> loadUrlFromDatabase(Connection connection, String sql) throws SQLException {
        List<String> results = new ArrayList<>();
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return results;
    }

    private static void storeIntoDatabaseIfItIsNewsPage(Document doc) {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                System.out.println(title);
            }
        }
    }

    @SuppressWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    private static Document httpGetAndParseHtml(String link) throws IOException {
        //这是我们感兴趣的，只处理新浪站内的链接
        //通过http拿到html
        CloseableHttpClient httpclient = HttpClients.createDefault();

        //先处理link，再创建http
        System.out.println(link);
        if (link.startsWith("//")) {
            link = "https" + link;
        }
        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.129 Safari/537.36");

        try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
            System.out.println(response1.getStatusLine());
            HttpEntity entity1 = response1.getEntity();
            String html = EntityUtils.toString(entity1);
            //jsoup解析html
            return Jsoup.parse(html);
        }
    }

    //对link条件的重构,我们只关心new.sina的，要排除登录页面
    private static boolean isInterestingLink(String link) {
        return (isNewsPage(link) || isIndexPage(link)) && isNotLoginPage(link);
    }

    private static boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isIndexPage(String link) {
        return "https://sina.cn".equals(link);
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn");
    }
}
