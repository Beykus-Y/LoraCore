package com.aiassist.api;

import com.aiassist.AiMod;
import com.aiassist.api.dto.OpenAiApiDto.*;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Клиент для взаимодействия с OpenAI-совместимым API.
 * Обрабатывает HTTP-запросы и парсинг базовых ответов.
 */
public class OpenAiApiClient {

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * Асинхронно запрашивает список доступных моделей.
     */
    public CompletableFuture<ModelsListResponse> fetchModelsAsync(String apiUrl, String apiKey) {
        CompletableFuture<ModelsListResponse> future = new CompletableFuture<>();
        Request request = new Request.Builder()
                .url(apiUrl + "/models")
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        executeRequest(request, ModelsListResponse.class, future);
        return future;
    }

    /**
     * Асинхронно отправляет запрос в чат.
     */
    public CompletableFuture<OpenAiResponse> createChatCompletionAsync(String apiUrl, String apiKey, ChatRequest payload) {
        CompletableFuture<OpenAiResponse> future = new CompletableFuture<>();
        try {
            String jsonBody = GSON.toJson(payload);
            RequestBody body = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(apiUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            executeRequest(request, OpenAiResponse.class, future);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private <T> void executeRequest(Request request, Class<T> responseClass, CompletableFuture<T> future) {
        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        String errorBody = responseBody != null ? responseBody.string() : "No response body";
                        future.completeExceptionally(new IOException("API request failed with code " + response.code() + ": " + errorBody));
                        return;
                    }

                    String bodyString = responseBody.string();
                    T result = GSON.fromJson(bodyString, responseClass);

                    if (result == null) {
                        future.completeExceptionally(new IOException("Failed to parse JSON response: " + bodyString));
                    } else {
                        future.complete(result);
                    }
                } catch (IOException | JsonSyntaxException e) {
                    future.completeExceptionally(e);
                }
            }
        });
    }
}