package com.service;

import com.core.log.ActivityLogHelper;
import com.dao.InvoiceCancelRequestDAO;
import com.dao.InvoiceDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ActivityLog;
import com.model.InvoiceCancelRequest;
import com.model.User;
import com.model.permission.AppPermission;

/**
 * Service layer bat buoc cho workflow huy hoa don co phe duyet.
 * UI khong duoc goi DAO de bo qua kiem tra permission/ownership.
 */
public class InvoiceCancelRequestService {

    private final InvoiceCancelRequestDAO requestDAO;
    private final InvoiceDAO invoiceDAO;
    private final AuthService authService;

    public InvoiceCancelRequestService() {
        this(new InvoiceCancelRequestDAO(), new InvoiceDAO(), AuthService.getInstance());
    }

    public InvoiceCancelRequestService(InvoiceCancelRequestDAO requestDAO,
                                       InvoiceDAO invoiceDAO,
                                       AuthService authService) {
        this.requestDAO = requestDAO;
        this.invoiceDAO = invoiceDAO;
        this.authService = authService;
    }

    public InvoiceCancelRequest getLatestForInvoice(int invoiceId) {
        return requestDAO.findLatestByInvoiceId(invoiceId);
    }

    public InvoiceCancelRequest getActiveForInvoice(int invoiceId) {
        return requestDAO.findActiveByInvoiceId(invoiceId);
    }

    public String requestCancel(int invoiceId, String reason) {
        User current = authService.getCurrentUser();
        if (current == null) {
            return "Phiên đăng nhập không hợp lệ.";
        }
        if (!authService.can(AppPermission.INVOICE_CANCEL_REQUEST)) {
            return "Bạn không có quyền gửi yêu cầu hủy hóa đơn.";
        }

        String error = requestDAO.createRequest(invoiceId, current.getUserId(), reason);
        if (error != null) {
            return error;
        }

        InvoiceCancelRequest created = requestDAO.findLatestByInvoiceId(invoiceId);
        ActivityLogHelper.record(
                "yêu cầu hủy hóa đơn",
                ActivityLog.ACTION_INVOICE_CANCEL_REQUEST,
                "Gửi yêu cầu hủy hóa đơn " + displayInvoice(created) + ".",
                null,
                created);
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));
        return null;
    }

    public String approve(int requestId, String reviewNote) {
        User reviewer = authService.getCurrentUser();
        if (!canReview(reviewer)) {
            return "Bạn không có quyền duyệt yêu cầu hủy hóa đơn.";
        }

        InvoiceCancelRequest claimed = requestDAO.claimPendingForReview(requestId, reviewer.getUserId());
        if (claimed == null) {
            return "Yêu cầu này không còn ở trạng thái chờ duyệt hoặc đã được người khác xử lý.";
        }

        String cancelError = invoiceDAO.cancelInvoice(claimed.getInvoiceId(), claimed.getReason(), null);
        if (cancelError != null) {
            requestDAO.releaseProcessing(requestId, reviewer.getUserId(), "Duyệt thất bại: " + cancelError);
            return cancelError;
        }

        if (!requestDAO.markApproved(requestId, reviewer.getUserId(), reviewNote)) {
            // Hoa don co the da CANCELLED. Reconcile de khong de request ket PROCESSING.
            requestDAO.reconcileForInvoice(claimed.getInvoiceId());
            InvoiceCancelRequest afterRecovery = requestDAO.findLatestByInvoiceId(claimed.getInvoiceId());
            if (afterRecovery == null || !afterRecovery.isApproved()) {
                return "Hóa đơn đã được hủy nhưng chưa cập nhật được trạng thái yêu cầu. Vui lòng mở lại để hệ thống đối soát.";
            }
        }

        InvoiceCancelRequest approved = requestDAO.findById(requestId);
        ActivityLogHelper.record(
                "yêu cầu hủy hóa đơn",
                ActivityLog.ACTION_INVOICE_CANCEL_APPROVE,
                "Duyệt yêu cầu hủy hóa đơn " + displayInvoice(approved) + ".",
                claimed,
                approved);
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));
        return null;
    }

    public String reject(int requestId, String reviewNote) {
        User reviewer = authService.getCurrentUser();
        if (!canReview(reviewer)) {
            return "Bạn không có quyền từ chối yêu cầu hủy hóa đơn.";
        }
        if (reviewNote == null || reviewNote.isBlank()) {
            return "Vui lòng nhập lý do từ chối.";
        }

        InvoiceCancelRequest before = requestDAO.findById(requestId);
        if (before == null || !before.isPending()) {
            return "Yêu cầu này không còn ở trạng thái chờ duyệt.";
        }

        if (!requestDAO.rejectPending(requestId, reviewer.getUserId(), reviewNote)) {
            return "Không thể từ chối yêu cầu. Có thể yêu cầu đã được người khác xử lý.";
        }

        InvoiceCancelRequest rejected = requestDAO.findById(requestId);
        ActivityLogHelper.record(
                "yêu cầu hủy hóa đơn",
                ActivityLog.ACTION_INVOICE_CANCEL_REJECT,
                "Từ chối yêu cầu hủy hóa đơn " + displayInvoice(rejected) + ".",
                before,
                rejected);
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));
        return null;
    }

    private boolean canReview(User user) {
        return user != null
                && authService.can(AppPermission.INVOICE_CANCEL)
                && authService.can(AppPermission.INVOICE_VIEW_ALL);
    }

    private String displayInvoice(InvoiceCancelRequest request) {
        if (request == null) {
            return "";
        }
        if (request.getInvoiceCode() != null && !request.getInvoiceCode().isBlank()) {
            return request.getInvoiceCode();
        }
        return "#" + request.getInvoiceId();
    }
}
