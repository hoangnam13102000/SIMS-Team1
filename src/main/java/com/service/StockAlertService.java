package com.service;

import com.dao.StockAlertDAO;
import com.model.Product;
import com.model.StockAlert;
import com.model.User;
import com.model.permission.AppPermission;

public class StockAlertService {

    private final StockAlertDAO stockAlertDAO = new StockAlertDAO();

    public boolean hasActiveAlert(int productId) {
        return stockAlertDAO.hasActiveAlert(productId);
    }

    public boolean reportStockAlert(Product product, String note) {
        if (product == null) {
            return false;
        }

        AuthService authService = AuthService.getInstance();

        User currentUser = authService.getCurrentUser();

        if (currentUser == null) {
            return false;
        }

        if (!authService.can(AppPermission.STOCK_ALERT_REPORT)) {
            return false;
        }

        if (stockAlertDAO.hasActiveAlert(product.getProductId())) {
            return false;
        }

        StockAlert alert = new StockAlert();

        alert.setProductId(product.getProductId());

        alert.setAlertType(
                product.getStock() <= 0
                        ? "OUT_OF_STOCK"
                        : "LOW_STOCK"
        );

        alert.setStockAtReport(product.getStock());

        if (note == null || note.isBlank()) {
            alert.setNote(null);
        } else {
            alert.setNote(note.trim());
        }

        // Không lấy UserID từ UI.
        // Luôn lấy tài khoản đang đăng nhập.
        alert.setReportedBy(currentUser.getUserId());

        return stockAlertDAO.create(alert);
    }
}
