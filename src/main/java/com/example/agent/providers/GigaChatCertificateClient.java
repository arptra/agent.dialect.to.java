package com.example.agent.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Client for GigaChat using mTLS authentication with certificate files.
 */
public class GigaChatCertificateClient implements LlmProvider {

    private final String baseUrl;
    private final String model;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GigaChatCertificateClient(String baseUrl, String model,
                                     Path certFile, Path keyFile, Path caFile)
            throws GeneralSecurityException, IOException {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
        this.model = model;
        this.http = buildClient(certFile, keyFile, caFile);
    }

    private OkHttpClient buildClient(Path certFile, Path keyFile, Path caFile)
            throws GeneralSecurityException, IOException {
        X509Certificate cert = readCert(certFile);
        PrivateKey key = readKey(keyFile);
        X509Certificate ca = readCert(caFile);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("client", key, new char[0], new Certificate[]{cert});

        KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
        ts.load(null, null);
        ts.setCertificateEntry("ca", ca);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];

        return new OkHttpClient.Builder()
                .sslSocketFactory(ctx.getSocketFactory(), tm)
                .callTimeout(Duration.ofSeconds(60))
                .build();
    }

    private X509Certificate readCert(Path path) throws IOException, GeneralSecurityException {
        try (InputStream in = Files.newInputStream(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private PrivateKey readKey(Path path) throws IOException, GeneralSecurityException {
        String pem = Files.readString(path);
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                 .replace("-----END PRIVATE KEY-----", "")
                 .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (GeneralSecurityException e) {
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        }
    }

    @Override
    public String chat(List<Map<String, String>> messages, double temperature) throws IOException {
        String url = baseUrl + "/v1/chat/completions";
        var payload = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature
        );
        RequestBody body = RequestBody.create(
                mapper.writeValueAsBytes(payload),
                MediaType.parse("application/json")
        );
        Request req = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GigaChat API error: " + resp.code() + " " + resp.message() + " body=" + (resp.body() != null ? resp.body().string() : ""));
            }
            JsonNode json = mapper.readTree(resp.body().bytes());
            return json.get("choices").get(0).get("message").get("content").asText();
        }
    }
}
