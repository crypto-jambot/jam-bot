package dev.rabbit.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.concurrent.TimeUnit;

@Configuration
public class Web3Configuration {

    @Bean
    public Web3j web3j() {
        return Web3j.build(httpService());
    }

    @Bean
    public HttpService httpService() {
        String NODE_ENDPOINT = "https://rabbit.dev/http";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .readTimeout(10, TimeUnit.MINUTES)
                .build();
        return new HttpService(NODE_ENDPOINT, client);
    }
}
