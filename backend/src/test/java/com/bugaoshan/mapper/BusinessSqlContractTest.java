package com.bugaoshan.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessSqlContractTest {

    @Test
    void hiddenCompletedOrdersStillCountAgainstExchangeLimit() throws Exception {
        String sql = mappedSql("OrderMapper", "countByUserIdAndGoodsId",
                Map.of("userId", 8L, "goodsId", 2L));

        assertTrue(sql.contains("status != 4"), "Cancelled orders must release the allowance");
        assertFalse(sql.contains("is_deleted"), "Hiding a completed order must not reset its allowance");
    }

    @Test
    void historicalOrderCanRestoreStockOfSoftDeletedGoods() throws Exception {
        String sql = mappedSql("GoodsMapper", "incrementStock", Map.of("goodsId", 2L));

        assertTrue(sql.contains("stock = stock + 1"));
        assertFalse(sql.contains("is_deleted"), "Soft deleting goods must not block historical refunds");
    }

    static Configuration mapperConfiguration(String mapper) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/" + mapper + ".xml";
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String mappedSql(String mapper, String statement, Map<String, Object> parameters) throws Exception {
        return mapperConfiguration(mapper)
                .getMappedStatement("com.bugaoshan.mapper." + mapper + "." + statement)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
    }
}
