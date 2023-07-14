package org.example;

import it.geosolutions.geoserver.rest.GeoServerRESTManager;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import it.geosolutions.geoserver.rest.decoder.RESTLayer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dom4j.Document;
import org.dom4j.io.SAXReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

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

    private HttpGet getHttpGet(String httpUrl, String contentType) {
        HttpGet httpGet = new HttpGet(httpUrl);
        httpGet.setHeader("Authorization", "Basic " + getUserEncoding());
        httpGet.setHeader("Content-type", contentType);
        return httpGet;
    }

    private String getUserEncoding() {
        return Base64.getUrlEncoder().encodeToString((geoserverUserName + ":" + geoserverPassWord).getBytes());
    }

    public GeoServerRESTManager getGeoServerRestManager() {
        try {
            return new GeoServerRESTManager(new URL(geoserverUrl), geoserverUserName, geoserverPassWord);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
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
            httpClient.close();
            FileUtils.deleteQuietly(file);
        } catch (Exception e) {
            log.error("----------添加本地二维数据源失败，{}----------", e.getMessage());
        }
        log.info("----------添加本地二维数据源end，返回结果{}----------", resp);
        return true;
    }

    private File getTiffXml(String workspaces, String storeName, String dataPath, String fileName) {
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
        PublishServiceUtilsTest publishServiceUtilsTest = new PublishServiceUtilsTest();
        String workspaces = "test";
        String storeName = "s_" + System.currentTimeMillis();
        String layerName = "l_" + System.currentTimeMillis();
        publishServiceUtilsTest.addTiffNewLocalStore(workspaces, storeName, "D:\\tiff", "");
        publishServiceUtilsTest.addNewLayer(workspaces, storeName, layerName, "EPSG:4326");
        GeoServerRESTManager manager = publishServiceUtilsTest.getGeoServerRestManager();
        GeoServerRESTReader reader = manager.getReader();
        RESTLayer layer = reader.getLayer(workspaces, layerName);
        System.out.println(publishServiceUtilsTest.getOpenLayerUrl(workspaces, layerName, layer));
    }

    private String getOpenLayerUrl(String workspace, String layerName, RESTLayer layer) {
        String[] coordinates = getFeatures(layer.getResourceUrl());
        String str = Arrays.stream(coordinates).limit(4).map(Double::parseDouble).map(String::valueOf).collect(Collectors.joining(","));
        return geoserverUrl + "/" + workspace + "/wfs?service=WMS&version=1.1.0" +
                "&request=GetMap" + "&layers=" + workspace + ":" + layerName + "&bbox=" + str + "&width=768&height=762&srs=" +
                coordinates[4] + "&styles=&format=application/openlayers";
    }

    private File getFeaturesFile(String resourceUrl) {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();) {
            File destFile = new File(System.currentTimeMillis() + ".xml");
            HttpGet get = getHttpGet(resourceUrl, "text/xml");
            CloseableHttpResponse response = httpClient.execute(get);
            FileUtils.copyInputStreamToFile(response.getEntity().getContent(), destFile);
            return destFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String[] getFeatures(String resourceUrl) {
        File file = getFeaturesFile(resourceUrl);
        try {
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(file);
            //读取文件 转换成Document
            String[] coordinates = new String[5];
            coordinates[0] = document.selectSingleNode("coverage/latLonBoundingBox/minx").getText();
            coordinates[1] = document.selectSingleNode("coverage/latLonBoundingBox/miny").getText();
            coordinates[2] = document.selectSingleNode("coverage/latLonBoundingBox/maxx").getText();
            coordinates[3] = document.selectSingleNode("coverage/latLonBoundingBox/maxy").getText();
            coordinates[4] = document.selectSingleNode("coverage/latLonBoundingBox/crs").getText();
            return coordinates;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            FileUtils.deleteQuietly(file);
        }
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
