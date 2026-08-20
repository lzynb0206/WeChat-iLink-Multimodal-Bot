package com.example.demo.tool;

import com.example.demo.config.AiConfig;
import com.example.demo.service.ai.AlibabaToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewsToolTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchesNewsAndReturnsStructuredResult() throws Exception {
        StubAlibabaToolService service = new StubAlibabaToolService();
        NewsTool tool = new NewsTool(service);

        JsonNode result = objectMapper.readTree(tool.execute(objectMapper.readTree(
                "{\"query\":\"人工智能\",\"limit\":3}")));

        assertEquals("人工智能", result.path("query").asText());
        assertEquals("新闻结果和来源链接", result.path("result").asText());
        assertEquals("人工智能", service.query);
        assertEquals(3, service.limit);
    }

    @Test
    void rejectsInvalidLimit() throws Exception {
        NewsTool tool = new NewsTool(new StubAlibabaToolService());
        JsonNode arguments = objectMapper.readTree("{\"query\":\"AI\",\"limit\":11}");

        assertThrows(IllegalArgumentException.class, () -> tool.execute(arguments));
    }

    private static class StubAlibabaToolService extends AlibabaToolService {
        private String query;
        private int limit;

        StubAlibabaToolService() {
            super(new AiConfig());
        }

        @Override
        public String searchNews(String query, int limit) {
            this.query = query;
            this.limit = limit;
            return "新闻结果和来源链接";
        }
    }
}
