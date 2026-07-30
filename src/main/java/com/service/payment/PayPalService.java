package com.service.payment;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.security.AppConfig;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class PayPalService {

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String clientId;
    private final String secret;
    private final BigDecimal vndToUsdRate;

    public PayPalService() {
        AppConfig config = AppConfig.getInstance();
        this.baseUrl = config.get("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com");
        this.clientId = config.get("PAYPAL_CLIENT_ID");
        this.secret = config.get("PAYPAL_SECRET");
        this.vndToUsdRate = new BigDecimal(config.get("VND_TO_USD_RATE", "26000"));
    }

    /** Kết quả sau khi khách xác nhận (hoặc hủy) trên trang PayPal. */
    public record ApprovalResult(boolean approved, String payPalOrderId) {}

    /** Kết quả sau khi capture (chốt) giao dịch. */
    public record CaptureResult(boolean success, String captureId, String status) {}

    /** Kết quả tạo đơn PayPal: id đơn + link để mở trình duyệt cho khách approve. */
    public record CreatedOrder(String payPalOrderId, String approveUrl) {}

    // ==================== 1) Lấy access token ====================

    private String fetchAccessToken() throws IOException, InterruptedException {
        String creds = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/oauth2/token"))
                .header("Authorization", "Basic " + creds)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Lấy access token PayPal thất bại (HTTP " + res.statusCode() + "): " + res.body());
        }
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        return json.get("access_token").getAsString();
    }

    // ==================== 2) Tạo đơn PayPal ====================

    /**
     * Tạo 1 đơn PayPal ứng với {@code totalVnd} (được quy đổi sang USD theo
     * VND_TO_USD_RATE, làm tròn 2 chữ số thập phân, tối thiểu 1.00 USD theo
     * yêu cầu của PayPal). {@code returnUrl}/{@code cancelUrl} trỏ về HTTP
     * server cục bộ đang chờ redirect (xem {@link #waitForApproval}).
     */
    public CreatedOrder createOrder(BigDecimal totalVnd, String referenceCode,
                                     String returnUrl, String cancelUrl) throws IOException, InterruptedException {
        String accessToken = fetchAccessToken();

        BigDecimal usd = totalVnd.divide(vndToUsdRate, 2, RoundingMode.HALF_UP);
        if (usd.compareTo(new BigDecimal("1.00")) < 0) usd = new BigDecimal("1.00");

        String body = "{"
                + "\"intent\":\"CAPTURE\","
                + "\"purchase_units\":[{"
                + "\"reference_id\":\"" + escape(referenceCode) + "\","
                + "\"amount\":{\"currency_code\":\"USD\",\"value\":\"" + usd + "\"}"
                + "}],"
                + "\"application_context\":{"
                + "\"return_url\":\"" + escape(returnUrl) + "\","
                + "\"cancel_url\":\"" + escape(cancelUrl) + "\","
                + "\"user_action\":\"PAY_NOW\","
                + "\"brand_name\":\"SIMS - Connect Mart\""
                + "}"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/checkout/orders"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 201) {
            throw new IOException("Tạo đơn PayPal thất bại (HTTP " + res.statusCode() + "): " + res.body());
        }

        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        String orderId = json.get("id").getAsString();
        String approveUrl = null;
        for (var link : json.getAsJsonArray("links")) {
            JsonObject l = link.getAsJsonObject();
            if ("approve".equals(l.get("rel").getAsString())) {
                approveUrl = l.get("href").getAsString();
                break;
            }
        }
        if (approveUrl == null) throw new IOException("Phản hồi PayPal thiếu link 'approve'.");
        return new CreatedOrder(orderId, approveUrl);
    }

    // ==================== 3) Mở trình duyệt ====================

    public void openApprovalPage(String approveUrl) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Máy này không hỗ trợ tự mở trình duyệt. Vui lòng mở thủ công: " + approveUrl);
        }
        try {
            Desktop.getDesktop().browse(URI.create(approveUrl));
        } catch (IOException e) {
            throw new IOException("Không mở được trình duyệt cho PayPal: " + e.getMessage(), e);
        }
    }

    // ==================== 4) Server cục bộ chờ redirect ====================

    /** 1 server tạm cho 1 lần thanh toán - start() rồi lấy port() để build return/cancel URL, chờ waitForApproval(), rồi stop(). */
    public static final class LocalCallbackServer {
        private final HttpServer server;
        private final CompletableFuture<ApprovalResult> resultFuture = new CompletableFuture<>();

        LocalCallbackServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/paypal/return", this::handleReturn);
            server.createContext("/paypal/cancel", this::handleCancel);
            server.setExecutor(null);
        }

        public void start() { server.start(); }

        public int port() { return server.getAddress().getPort(); }

        public String returnUrl() { return "http://127.0.0.1:" + port() + "/paypal/return"; }

        public String cancelUrl() { return "http://127.0.0.1:" + port() + "/paypal/cancel"; }

        /** Chờ (chặn luồng gọi) đến khi có redirect hoặc hết thời gian chờ. */
        public ApprovalResult await(Duration timeout) throws TimeoutException {
            try {
                return resultFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw e;
            } catch (Exception e) {
                return new ApprovalResult(false, null);
            }
        }

        public void stop() { server.stop(0); }

        private void handleReturn(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String token = extractParam(query, "token"); // PayPal tra ve OrderID qua param "token"
            respond(exchange, "<html><body style='font-family:sans-serif;text-align:center;padding-top:60px'>"
                    + "<h2>Thanh toán thành công!</h2><p>Bạn có thể đóng tab này và quay lại ứng dụng SIMS.</p></body></html>");
            resultFuture.complete(new ApprovalResult(true, token));
        }

        private void handleCancel(HttpExchange exchange) throws IOException {
            respond(exchange, "<html><body style='font-family:sans-serif;text-align:center;padding-top:60px'>"
                    + "<h2>Đã hủy thanh toán</h2><p>Bạn có thể đóng tab này và quay lại ứng dụng SIMS.</p></body></html>");
            resultFuture.complete(new ApprovalResult(false, null));
        }

        private void respond(HttpExchange exchange, String html) throws IOException {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private static String extractParam(String query, String key) {
            if (query == null) return null;
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals(key)) return kv[1];
            }
            return null;
        }
    }

    public LocalCallbackServer startLocalCallbackServer() throws IOException {
        LocalCallbackServer server = new LocalCallbackServer();
        server.start();
        return server;
    }

    // ==================== 5) Capture (chốt giao dịch) ====================

    public CaptureResult captureOrder(String payPalOrderId) throws IOException, InterruptedException {
        String accessToken = fetchAccessToken();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/checkout/orders/" + payPalOrderId + "/capture"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 201 && res.statusCode() != 200) {
            AppLogger.getInstance().error(ErrorCode.ORDER_CHECKOUT_FAIL,
                    "PayPalService.captureOrder - HTTP " + res.statusCode() + ": " + res.body(), null);
            return new CaptureResult(false, null, "FAILED");
        }

        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        String status = json.get("status").getAsString();
        String captureId = null;
        try {
            captureId = json.getAsJsonArray("purchase_units").get(0).getAsJsonObject()
                    .getAsJsonObject("payments")
                    .getAsJsonArray("captures").get(0).getAsJsonObject()
                    .get("id").getAsString();
        } catch (Exception ignored) {
            // Cau truc phan hoi khac thuong - van tra ve status de noi goi tu quyet dinh.
        }
        return new CaptureResult("COMPLETED".equals(status), captureId, status);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}