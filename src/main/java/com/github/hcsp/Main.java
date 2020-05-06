package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws IOException {
        //待处理的链接池
        List<String> linkPool = new ArrayList<>();
        //已处理的链接池
        Set<String> processedlinks = new HashSet<>();
        linkPool.add("https://sina.cn");

        while (true) {
            if (linkPool.isEmpty()) {
                break;
            }
            String link = linkPool.get(linkPool.size() - 1);
            //ArrayList从尾部删除更有效率
            linkPool.remove(linkPool.size() - 1);

            if (processedlinks.contains(link)) {
                continue;
            }

//            if ((link.contains("news.sina.cn") || "https://sina.cn".equals(link)) && !link.contains("passport.sina.cn")) {
            if (isInterestingLink(link)) {

                Document doc = httpGetAndParseHtml(link);
//                select方法返回的是Element，Element继承了ArrayList
//                ArrayList<Element> links = doc.select("a");

                //attr获取该属性值，添加到链接池,简化上下两个语句
                doc.select("a").stream().map(aTag -> aTag.attr("href")).forEach(linkPool::add);
//                for (Element aTag : links) {
//                    linkPool.add(aTag.attr("href"));
//                }

                //假如是一个新闻的详情页，就存入数据库，否则什么都不做
                storeIntoDatabaseIfItIsNewsPage(doc);
                processedlinks.add(link);
            } else {
                //这是不感兴趣的，不处理
                continue;
            }
        }


    }

    private static void storeIntoDatabaseIfItIsNewsPage(Document doc) {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
            }
        }
    }

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

    //对link条件的重构
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
