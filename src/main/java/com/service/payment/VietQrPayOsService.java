package com.service.payment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.security.AppConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tích hợp VietQR qua payOS cho POS desktop.
 *
 * Luồng: 1) Tạo payment request với số tiền VND cố định. 2) payOS trả qrCode
 * dạng EMV/VietQR -> PosPanel tự render bằng ZXing. 3) POS polling GET payment
 * request để tự xác nhận PAID/CANCELLED/PENDING.
 */
public final class VietQrPayOsService {

	private static final AtomicLong LAST_ORDER_CODE = new AtomicLong();

	private final HttpClient http;
	private final String baseUrl;
	private final String clientId;
	private final String apiKey;
	private final String checksumKey;
	private final String returnUrl;
	private final String cancelUrl;
	private final int expireSeconds;
	private final long pollIntervalMillis;

	public record CreatedPayment(long orderCode, long amount, String description, String paymentLinkId,
			String checkoutUrl, String qrContent, String status) {
	}

	public record PaymentStatus(long orderCode, long amount, long amountPaid, long amountRemaining, String status,
			String paymentLinkId, String reference) {

		public boolean isPaid() {
			return "PAID".equalsIgnoreCase(status) && amountPaid >= amount && amountRemaining <= 0;
		}

		public boolean isCancelledOrExpired() {
			return "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status);
		}
	}

	public VietQrPayOsService() {
		AppConfig config = AppConfig.getInstance();

		this.baseUrl = trimTrailingSlash(config.get("PAYOS_BASE_URL", "https://api-merchant.payos.vn"));
		this.clientId = requireConfigured(config.get("PAYOS_CLIENT_ID"), "PAYOS_CLIENT_ID");
		this.apiKey = requireConfigured(config.get("PAYOS_API_KEY"), "PAYOS_API_KEY");
		this.checksumKey = requireConfigured(config.get("PAYOS_CHECKSUM_KEY"), "PAYOS_CHECKSUM_KEY");
		this.returnUrl = config.get("PAYOS_RETURN_URL", "https://payos.vn");
		this.cancelUrl = config.get("PAYOS_CANCEL_URL", "https://payos.vn");
		this.expireSeconds = Math.max(60, config.getInt("PAYOS_QR_EXPIRE_SECONDS", 300));
		this.pollIntervalMillis = Math.max(1000L, config.getLong("PAYOS_POLL_INTERVAL_MS", 2000L));

		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	}

	public long getPollIntervalMillis() {
		return pollIntervalMillis;
	}

	public int getExpireSeconds() {
		return expireSeconds;
	}

	public CreatedPayment createPayment(long amountVnd) throws IOException, InterruptedException {
		if (amountVnd <= 0) {
			throw new IllegalArgumentException("Số tiền VietQR phải lớn hơn 0 VND.");
		}

		long orderCode = nextOrderCode();
		String description = buildDescription(orderCode);
		long expiredAt = Instant.now().plusSeconds(expireSeconds).getEpochSecond();

		String signature = signCreatePayment(amountVnd, orderCode, description);

		JsonObject body = new JsonObject();
		body.addProperty("orderCode", orderCode);
		body.addProperty("amount", amountVnd);
		body.addProperty("description", description);
		body.addProperty("cancelUrl", cancelUrl);
		body.addProperty("returnUrl", returnUrl);
		body.addProperty("expiredAt", expiredAt);
		body.addProperty("signature", signature);

		HttpRequest request = baseRequest(baseUrl + "/v2/payment-requests").header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.timeout(Duration.ofSeconds(20)).build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject root = parseSuccessResponse(response, "Tạo VietQR");
		JsonObject data = requiredObject(root, "data");

		return new CreatedPayment(getLong(data, "orderCode", orderCode), getLong(data, "amount", amountVnd),
				getString(data, "description"), getString(data, "paymentLinkId"), getString(data, "checkoutUrl"),
				getString(data, "qrCode"), getString(data, "status"));
	}

	public PaymentStatus getPaymentStatus(String paymentLinkIdOrOrderCode) throws IOException, InterruptedException {
		if (paymentLinkIdOrOrderCode == null || paymentLinkIdOrOrderCode.isBlank()) {
			throw new IllegalArgumentException("Thiếu mã giao dịch payOS.");
		}

		HttpRequest request = baseRequest(baseUrl + "/v2/payment-requests/" + paymentLinkIdOrOrderCode.trim()).GET()
				.timeout(Duration.ofSeconds(15)).build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject root = parseSuccessResponse(response, "Kiểm tra trạng thái VietQR");
		JsonObject data = requiredObject(root, "data");

		long amount = getLong(data, "amount", 0);
		long amountPaid = getLong(data, "amountPaid", 0);
		long amountRemaining = getLong(data, "amountRemaining", Math.max(0, amount - amountPaid));

		return new PaymentStatus(getLong(data, "orderCode", 0), amount, amountPaid, amountRemaining,
				getString(data, "status"), firstNonBlank(getString(data, "id"), paymentLinkIdOrOrderCode),
				extractReference(data));
	}

	public PaymentStatus cancelPayment(String paymentLinkIdOrOrderCode, String reason)
			throws IOException, InterruptedException {
		if (paymentLinkIdOrOrderCode == null || paymentLinkIdOrOrderCode.isBlank()) {
			throw new IllegalArgumentException("Thiếu mã giao dịch payOS.");
		}

		JsonObject body = new JsonObject();
		body.addProperty("cancellationReason", reason == null || reason.isBlank() ? "POS cancelled" : reason.trim());

		HttpRequest request = baseRequest(
				baseUrl + "/v2/payment-requests/" + paymentLinkIdOrOrderCode.trim() + "/cancel")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.timeout(Duration.ofSeconds(15)).build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject data = requiredObject(root, "data");
			long amount = getLong(data, "amount", 0);
			long amountPaid = getLong(data, "amountPaid", 0);
			long remaining = getLong(data, "amountRemaining", Math.max(0, amount - amountPaid));
			return new PaymentStatus(getLong(data, "orderCode", 0), amount, amountPaid, remaining,
					getString(data, "status"), firstNonBlank(getString(data, "id"), paymentLinkIdOrOrderCode),
					extractReference(data));
		}

		// Có thể giao dịch vừa PAID đúng lúc người dùng bấm Hủy.
		// Không suy luận từ lỗi cancel; đọc lại trạng thái thật trước khi quyết định.
		return getPaymentStatus(paymentLinkIdOrOrderCode);
	}

	public void openCheckoutPage(String checkoutUrl) throws IOException {
		if (checkoutUrl == null || checkoutUrl.isBlank()) {
			throw new IOException("Không có đường dẫn trang thanh toán payOS.");
		}
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			throw new IOException("Máy này không hỗ trợ tự mở trình duyệt.");
		}
		Desktop.getDesktop().browse(URI.create(checkoutUrl));
	}

	private HttpRequest.Builder baseRequest(String url) {
		return HttpRequest.newBuilder().uri(URI.create(url)).header("x-client-id", clientId).header("x-api-key", apiKey)
				.header("Accept", "application/json");
	}

	private JsonObject parseSuccessResponse(HttpResponse<String> response, String action) throws IOException {
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException(action + " thất bại (HTTP " + response.statusCode() + "): " + response.body());
		}

		JsonObject root;
		try {
			root = JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException(action + " trả về dữ liệu không hợp lệ.", e);
		}

		String code = getString(root, "code");
		if (code != null && !code.isBlank() && !"00".equals(code)) {
			throw new IOException(action + " thất bại: " + firstNonBlank(getString(root, "desc"), code));
		}
		return root;
	}

	private String signCreatePayment(long amount, long orderCode, String description) {
		String data = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description + "&orderCode="
				+ orderCode + "&returnUrl=" + returnUrl;
		return hmacSha256Hex(data, checksumKey);
	}

	private static String hmacSha256Hex(String data, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
			}
			return out.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Không thể tạo chữ ký payOS.", e);
		}
	}

	private static long nextOrderCode() {
		return LAST_ORDER_CODE.updateAndGet(previous -> {
			long now = System.currentTimeMillis();
			return Math.max(now, previous + 1);
		});
	}

	/**
	 * Dùng mô tả 9 ký tự để tương thích cả kênh ngân hàng có giới hạn description
	 * ngắn.
	 */
	private static String buildDescription(long orderCode) {
		String raw = Long.toString(Math.abs(orderCode));
		String suffix = raw.length() > 5 ? raw.substring(raw.length() - 5) : raw;
		return "SIMS" + suffix;
	}

	private static String extractReference(JsonObject data) {
		JsonElement transactions = data.get("transactions");
		if (transactions == null || transactions.isJsonNull()) {
			return null;
		}

		try {
			if (transactions.isJsonArray() && transactions.getAsJsonArray().size() > 0) {
				JsonElement last = transactions.getAsJsonArray().get(transactions.getAsJsonArray().size() - 1);
				if (last.isJsonObject()) {
					return getString(last.getAsJsonObject(), "reference");
				}
			}
			if (transactions.isJsonObject()) {
				String direct = getString(transactions.getAsJsonObject(), "reference");
				if (direct != null && !direct.isBlank()) {
					return direct;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static JsonObject requiredObject(JsonObject root, String name) throws IOException {
		JsonElement value = root.get(name);
		if (value == null || !value.isJsonObject()) {
			throw new IOException("Phản hồi payOS thiếu trường '" + name + "'.");
		}
		return value.getAsJsonObject();
	}

	private static String getString(JsonObject object, String name) {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull()) {
			return null;
		}
		try {
			return value.getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private static long getLong(JsonObject object, String name, long defaultValue) {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull()) {
			return defaultValue;
		}
		try {
			return value.getAsLong();
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private static String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : second;
	}

	private static String requireConfigured(String value, String key) {
		if (value == null || value.isBlank() || value.startsWith("YOUR_")) {
			throw new IllegalStateException("Chưa cấu hình " + key + " cho payOS/VietQR.");
		}
		return value.trim();
	}

	private static String trimTrailingSlash(String value) {
		if (value == null) {
			return "";
		}
		String out = value.trim();
		while (out.endsWith("/")) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}
}