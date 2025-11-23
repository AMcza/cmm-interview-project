package com.yupi.springbootinit;

import com.yupi.springbootinit.esdao.QuestionEsDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateResponse;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * elasticsearch Template测试
 */
@SpringBootTest
class EsTests {

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    private final String INDEX_NAME="test_index";

    //创建一个doc
    @Test
    public void indexDocument() {
        Map<String,Object> doc=new HashMap<>();
        doc.put("title","Elasticsearch");
        doc.put("content","Learn Elasticsearch basics andn advanced usage");
        doc.put("tags","elasticsearch,search");
        doc.put("answer","Yes");
        doc.put("userId",1L);
        doc.put("editTime","2023-09-01 10:00:00");
        doc.put("createTime","2023-09-01 10:00:00");
        doc.put("updateTime","2023-09-01 10:00:00");
        doc.put("isDelete",false);

        IndexQuery indexQuery=new IndexQueryBuilder().withObject(doc).build();
        String documentId=elasticsearchRestTemplate.index(indexQuery, IndexCoordinates.of("1"));

        System.out.println(documentId);
        Assertions.assertNotNull(documentId);
    }

    //查询操作
    @Test
    public void getDocument(){
        String documentId="nkR-L5oBjoOlpHsu6yVp";
        Map<String,Object> document=elasticsearchRestTemplate.get(documentId,Map.class,IndexCoordinates.of("1"));

        Assertions.assertNotNull(document);
        System.out.println(document);
        System.out.println(document.get("title"));
    }

    //更新操作
    @Test
    public void updateDocument(){
        String documentId="nkR-L5oBjoOlpHsu6yVp";
        Map<String,Object> updates=new HashMap<>();
        updates.put("title","updated Elasicsearch Title");
        updates.put("updateTime","2025-09-01 10:00:00");

        UpdateQuery updateQuery=UpdateQuery.builder(documentId)
                .withDocument(Document.from(updates)).build();

        UpdateResponse update = elasticsearchRestTemplate.update(updateQuery, IndexCoordinates.of("1"));
        Assertions.assertNotNull(update);
        System.out.println(update);
    }

    //删除操作  删除索引
    @Test
    public void deleteDocument(){
        String documentId="nkR-L5oBjoOlpHsu6yVp";
        //索引操作
        IndexOperations indexOps=elasticsearchRestTemplate.indexOps(IndexCoordinates.of("1"));
        boolean deleted=indexOps.delete();
        Assertions.assertTrue(deleted);
    }

    @Resource
    private QuestionEsDao questionEsDao;

    @Test
    public void findByUserId(){

    }
}
