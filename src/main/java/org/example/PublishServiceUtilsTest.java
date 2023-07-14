package org.example;

import it.geosolutions.geoserver.rest.GeoServerRESTManager;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

@Slf4j
public class PublishServiceUtilsTest {

    private String geoserverUrl = "http://localhost:8080/geoserver";

    private String geoserverUserName = "admin";

    private String geoserverPassWord = "geoserver";

    private HttpPost getHttpPost(String httpUrl, String contentType) {
        HttpPost httpPost = new HttpPost(httpUrl);
        httpPost.setHeader("Authorization", "Basic " + getUserEncoding());
        httpPost.setHeader("Content-type", contentType);
        return httpPost;
    }

    private String getUserEncoding() {
        return Base64.getUrlEncoder().encodeToString((geoserverUserName + ":" + geoserverPassWord).getBytes());
    }

    public GeoServerRESTManager getGeoServerRestManager() {
        try {
            return new GeoServerRESTManager(new URL(geoserverUrl), geoserverUserName, geoserverPassWord);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e.getMessage());
        }
    }

    public boolean createWorkspace(GeoServerRESTReader reader, GeoServerRESTPublisher publisher, String workspace) {
        boolean flag = true;
        // 已有此工作区则不在创建
        if (!reader.existsWorkspace(workspace)) {
            // 创建工作区
            boolean b = publisher.createWorkspace(workspace);
            if (!b) {
                flag = false;
            }
        }
        return flag;
    }

    public boolean addTiffNewLocalStore(String workspaces, String storeName, String dataPath, String fileName) {
        GeoServerRESTManager manager = getGeoServerRestManager();
        GeoServerRESTReader reader = manager.getReader();
        GeoServerRESTPublisher publisher = manager.getPublisher();
        createWorkspace(reader, publisher, workspaces);
        File file = getTiffXml(workspaces, storeName, dataPath, fileName);
        String httpUrl = geoserverUrl + "/rest/workspaces/" + workspaces + "/coveragestores";
        // 创建httpPost
        CloseableHttpResponse resp = null;
        try {
            HttpPost httpPost = getHttpPost(httpUrl, "text/xml");
            httpPost.setEntity(new ByteArrayEntity(Files.readAllBytes(file.toPath())));
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            resp = httpClient.execute(httpPost);
            int code = resp.getStatusLine().getStatusCode();
            switch (code) {
                case HttpStatus.SC_OK:
                case HttpStatus.SC_CREATED:
                case HttpStatus.SC_ACCEPTED:
                    break;
                default:
                    log.error("----------添加本地二维数据源失败，{}----------", resp);
                    return false;
            }
            file.delete();
        } catch (Exception e) {
            log.error("----------添加本地二维数据源失败，{}----------", e.getMessage());
        }
        log.info("----------添加本地二维数据源end，返回结果{}----------", resp);
        return true;
    }

    private static File getTiffXml(String workspaces, String storeName, String dataPath, String fileName) {
        log.info("===getTiffXml===dataPath = {}", dataPath);
        String content = "<coverageStore>" +
                "<name>" + storeName + "</name>" +
                "<type>ImagePyramid</type>" +
                "<enabled>true</enabled>" +
                "<workspace>" + workspaces + "</workspace>" +
                "<url>file://" + dataPath + "</url>" +
                "</coverageStore>";

        File temp = new File(storeName + ".xml");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp.getName(), StandardCharsets.UTF_8))) {
            writer.write(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return temp;
    }

    public static void main(String[] args) throws IOException {
        new PublishServiceUtilsTest().addTiffNewLocalStore("test", "test4", "D:\\tiff", "");
        new PublishServiceUtilsTest().addNewLayer("test", "test4", "sadsa3", "EPSG:4326");
    }


    public Boolean addNewLayer(String workspaces, String storeName, String layerName, String srs) throws IOException {
        log.info("----------添加图层start----------");
        String httpUrl = geoserverUrl + "/rest/workspaces/" + workspaces + "/coveragestores/" + storeName + "/coverages";
        String xml = "<coverage>" +
                "<name>" + layerName + "</name>" +
                "<nativeName>" + layerName + "</nativeName>" +
                "<srs>" + srs + "</srs>" +
                "</coverage>";
        CloseableHttpResponse resp;
        //  创建httpPut并传递xml数据
        HttpPost httpPut = getHttpPost(httpUrl, "text/xml;charset=utf8");
        //获取浏览器信息
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        StringEntity entity = new StringEntity(xml, "UTF-8");
        httpPut.setEntity(entity);
        resp = httpClient.execute(httpPut);
        int code = resp.getStatusLine().getStatusCode();
        switch (code) {
            case HttpStatus.SC_OK:
            case HttpStatus.SC_CREATED:
            case HttpStatus.SC_ACCEPTED:
                break;
            default:
                throw new RuntimeException(resp.toString());
        }
        log.info("----------添加图层end，返回结果{}----------", resp);
        return true;
    }

}
