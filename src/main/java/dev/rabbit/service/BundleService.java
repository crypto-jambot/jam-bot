package dev.rabbit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rabbit.pojo.BlockBuilder;
import dev.rabbit.pojo.Path;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Service
@AllArgsConstructor
@Slf4j
public class BundleService {

    private final Credentials flashbotCredentials;
    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;
    private final Web3j web3j;

    public void sendBundles(List<Path> paths) throws IOException {
        BigInteger currentBlockNumber = web3j.ethBlockNumber().send().getBlockNumber();
        List<String> jsonBodies = new ArrayList<>();
        for (Path path : paths) {
            String jsonBody1 = mapper.writeValueAsString(
                    buildBody(path.getSignedTransaction(), currentBlockNumber.add(BigInteger.ONE)));
            String jsonBody2 = mapper.writeValueAsString(
                    buildBody(path.getSignedTransaction(), currentBlockNumber.add(BigInteger.TWO)));
            jsonBodies.add(jsonBody1);
            jsonBodies.add(jsonBody2);
        }

        List<Request> requests = new ArrayList<>();

        for (BlockBuilder builder : BlockBuilder.values()) {
            for (String body : jsonBodies) {
                requests.add(buildRequestBundle(builder.getUrl(), body, builder.isWithSignatureHeader()));
            }
        }

        CountDownLatch latch = new CountDownLatch(requests.size());
        // send each request asynchronously
        for (Request request : requests) {
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    latch.countDown();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    ResponseBody body = response.body();
                    if (ObjectUtils.isNotEmpty(body)) {
                        System.out.println(body.string());
                    }
                    latch.countDown();
                }
            });
        }

        // wait for all requests to complete
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(Arrays.toString(e.getStackTrace()));
        }
    }

    private String signMessage(String message) {
        String messageHash = Hash.sha3String(message);
        byte[] messageHashBytes = messageHash.getBytes(StandardCharsets.UTF_8);
        Sign.SignatureData signatureData =
                Sign.signPrefixedMessage(messageHashBytes, flashbotCredentials.getEcKeyPair());
        return Numeric.toHexString(signatureData.getR())
                + Numeric.toHexString(signatureData.getS()).substring(2)
                + Numeric.toHexString(signatureData.getV()).substring(2);
    }

    private Request buildRequestBundle(String url, String jsonBody, boolean withSignatureHeader) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request.Builder requestBuilder = new Request.Builder().method("POST", body)
                .url(url)
                .addHeader("Content-Type", "application/json");
        if (withSignatureHeader) {
            requestBuilder.addHeader("X-Flashbots-Signature",
                    flashbotCredentials.getAddress() + ":" + signMessage(jsonBody));
        }
        return requestBuilder.build();
    }

    private Map<String, Object> buildBody(String signedTransaction, BigInteger blockNumber) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "eth_sendBundle",
                "params", List.of(Map.of(
                        "txs", List.of(signedTransaction),
                        "blockNumber", Numeric.encodeQuantity(blockNumber))));
    }
}
