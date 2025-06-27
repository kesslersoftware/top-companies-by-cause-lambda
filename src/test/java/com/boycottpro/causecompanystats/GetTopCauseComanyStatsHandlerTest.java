package com.boycottpro.causecompanystats;

import com.amazonaws.services.lambda.runtime.Context;
import com.boycottpro.causecompanystats.GetTopCauseComanyStatsHandler;
import com.boycottpro.models.CauseCompanyStats;
import com.boycottpro.models.ResponseMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

@ExtendWith(MockitoExtension.class)
public class GetTopCauseComanyStatsHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private GetTopCauseComanyStatsHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();



    @Test
    public void testHandleRequest_success() throws Exception {
        String causeId = "cause123";

        Map<String, String> pathParams = Map.of("cause_id", causeId);
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent().withPathParameters(pathParams);

        Map<String, AttributeValue> item1 = Map.of(
                "cause_id",AttributeValue.fromS(causeId),
                "cause_desc",AttributeValue.fromS("cause_desc1"),
                "company_id", AttributeValue.fromS("company1"),
                "company_name", AttributeValue.fromS("Company One"),
                "boycott_count", AttributeValue.fromN("12")
        );

        Map<String, AttributeValue> item2 = Map.of(
                "cause_id",AttributeValue.fromS(causeId),
                "cause_desc",AttributeValue.fromS("cause_desc2"),
                "company_id", AttributeValue.fromS("company2"),
                "company_name", AttributeValue.fromS("Company Two"),
                "boycott_count", AttributeValue.fromN("10")
        );

        QueryResponse mockResponse = QueryResponse.builder()
                .items(List.of(item1, item2))
                .build();

        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(mockResponse);

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(200, response.getStatusCode());
        String expectedJson = objectMapper.writeValueAsString(List.of(
                new CauseCompanyStats("cause123", "cause_desc1","company1", "Company One", 12),
                new CauseCompanyStats("cause123", "cause_desc2","company2", "Company Two", 10)
        ));
        assertEquals(expectedJson, response.getBody());

        verify(dynamoDb, times(1)).query(any(QueryRequest.class));
    }

    @Test
    public void testHandleRequest_missingCauseId() throws JsonProcessingException {
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of());

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(400, response.getStatusCode());
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertTrue(message.getMessage().contains("sorry, there was an error processing your request"));
    }

    @Test
    public void testHandleRequest_exception() throws JsonProcessingException {
        String causeId = "cause123";
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("cause_id", causeId));

        when(dynamoDb.query(any(QueryRequest.class))).thenThrow(RuntimeException.class);

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(500, response.getStatusCode());
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertTrue(message.getMessage().contains("sorry, there was an error processing your request"));
    }
}
