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
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Arrays;

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
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                           .replaceAll("-----END [^-]+-----", "")
                           .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            return readRsaKey(der);
        } else if (pem.contains("BEGIN EC PRIVATE KEY")) {
            return readEcKey(der);
        } else {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (GeneralSecurityException e) {
                return KeyFactory.getInstance("EC").generatePrivate(spec);
            }
        }
    }

    private PrivateKey readRsaKey(byte[] pkcs1) throws GeneralSecurityException, IOException {
        DerReader r = new DerReader(pkcs1);
        r.expect(0x30); r.readLength();
        r.expect(0x02); int verLen = r.readLength(); r.skip(verLen);
        BigInteger n = r.readInteger();
        BigInteger e = r.readInteger();
        BigInteger d = r.readInteger();
        BigInteger p = r.readInteger();
        BigInteger q = r.readInteger();
        BigInteger dp = r.readInteger();
        BigInteger dq = r.readInteger();
        BigInteger qi = r.readInteger();
        RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qi);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PrivateKey readEcKey(byte[] sec1) throws GeneralSecurityException, IOException {
        DerReader r = new DerReader(sec1);
        r.expect(0x30); r.readLength();
        r.expect(0x02); r.readLength(); r.read(); // version
        byte[] priv = r.readOctetString();
        BigInteger s = new BigInteger(1, priv);
        ECParameterSpec params = null;
        if (r.hasRemaining()) {
            int tag = r.peek();
            if (tag == 0xA0) {
                r.read();
                int len = r.readLength();
                r.expect(0x06); int oidLen = r.readLength();
                byte[] oidBytes = r.readBytes(oidLen);
                String oid = decodeOid(oidBytes);
                AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
                ap.init(new ECGenParameterSpec(oid));
                params = ap.getParameterSpec(ECParameterSpec.class);
                r.skip(len - (2 + oidLen));
            }
        }
        ECPrivateKeySpec spec = new ECPrivateKeySpec(s, params);
        return KeyFactory.getInstance("EC").generatePrivate(spec);
    }

    private static String decodeOid(byte[] oid) {
        StringBuilder sb = new StringBuilder();
        int first = oid[0] & 0xff;
        sb.append(first / 40).append('.').append(first % 40);
        long val = 0;
        for (int i = 1; i < oid.length; i++) {
            int b = oid[i] & 0xff;
            val = (val << 7) | (b & 0x7f);
            if ((b & 0x80) == 0) {
                sb.append('.').append(val);
                val = 0;
            }
        }
        return sb.toString();
    }

    private static final class DerReader {
        private final byte[] data;
        private int pos;
        DerReader(byte[] data) { this.data = data; }
        boolean hasRemaining() { return pos < data.length; }
        int peek() { return data[pos] & 0xff; }
        int read() { return data[pos++] & 0xff; }
        void expect(int tag) throws IOException { if (read() != tag) throw new IOException("Unexpected DER tag"); }
        void skip(int len) { pos += len; }
        int readLength() {
            int b = read();
            if (b < 0x80) return b;
            int n = b & 0x7f;
            int len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | read();
            return len;
        }
        byte[] readBytes(int len) {
            byte[] out = Arrays.copyOfRange(data, pos, pos + len);
            pos += len;
            return out;
        }
        BigInteger readInteger() throws IOException {
            expect(0x02);
            int len = readLength();
            return new BigInteger(readBytes(len));
        }
        byte[] readOctetString() throws IOException {
            expect(0x04);
            int len = readLength();
            return readBytes(len);
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
