package com.boycottpro.causecompanystats;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.CauseCompanyStats;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.utilities.JwtUtility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class GetTopCauseComanyStatsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GetTopCauseComanyStatsHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public GetTopCauseComanyStatsHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String sub = null;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, Map.of("message", "Unauthorized"));
            Map<String, String> pathParams = event.getPathParameters();
            String causeId = (pathParams != null) ? pathParams.get("cause_id") : null;
            if (causeId == null || causeId.isEmpty()) {
                ResponseMessage message = new ResponseMessage(400,
                        "sorry, there was an error processing your request",
                        "cause_id is missing!");
                return response(400,message);
            }
            List<CauseCompanyStats> stats = getTopCompaniesByCause(causeId);
            return response(200,stats);
        } catch (Exception e) {
            System.out.println(e.getMessage() + " for user " + sub);
            return response(500,Map.of("error", "Unexpected server error: " + e.getMessage()) );
        }
    }
    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }
    private List<CauseCompanyStats> getTopCompaniesByCause(String causeId) {
        QueryRequest request = QueryRequest.builder()
                .tableName("cause_company_stats")
                .keyConditionExpression("cause_id = :cid")
                .expressionAttributeValues(Map.of(
                        ":cid", AttributeValue.fromS(causeId)
                ))
                .projectionExpression("cause_id, cause_desc, company_id, company_name, boycott_count")
                .scanIndexForward(false) // descending order
                .limit(3)
                .build();

        QueryResponse response = dynamoDb.query(request);

        return response.items().stream()
                .map(item -> {
                    CauseCompanyStats stats = new CauseCompanyStats();
                    stats.setCause_id(causeId);
                    stats.setCause_desc(item.getOrDefault("cause_desc",AttributeValue.fromS("")).s());
                    stats.setCompany_id(item.getOrDefault("company_id", AttributeValue.fromS("")).s());
                    stats.setCompany_name(item.getOrDefault("company_name", AttributeValue.fromS("")).s());
                    stats.setBoycott_count(Integer.parseInt(item.getOrDefault("boycott_count", AttributeValue.fromN("0")).n()));
                    return stats;
                })
                .collect(Collectors.toList());
    }

}