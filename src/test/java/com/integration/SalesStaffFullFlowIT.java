package com.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.dao.DashboardDAO;
import com.dao.InvoiceCancelRequestDAO;
import com.dao.InvoiceDAO;
import com.dao.OrderDAO;
import com.dao.ProductDAO;
import com.dao.ReturnExchangeDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.InvoicePayment;
import com.model.OrderDetail;
import com.model.Product;
import com.model.ReturnExchange;
import com.model.ReturnExchangeDetail;
import com.model.Shift;
import com.model.ShiftCashSummary;
import com.model.ShiftCashTransaction;
import com.model.User;
import com.service.AuthService;
import com.service.HeldCartService;
import com.service.InvoiceCancelRequestService;
import com.service.PosCartService;
import com.service.ShiftService;
import com.utils.DBConnection;

/**
 * Integration test end-to-end cho SALES_STAFF tren MySQL that.
 *
 * <p>Mac dinh KHONG chay khi mvn test. De chay, set:
 * SIMS_IT_RUN=true.
 *
 * <p>An toan: neu database name khong chua "test" / "it", test se tu choi chay
 * tru khi set them SIMS_IT_ALLOW_SHARED_DB=true. Nen dung ban clone SIMS_DB_TEST.
 *
 * <p>Suite tao du lieu co prefix IT_. Vi Invoices la du lieu nghiep vu immutable va
 * DB co trigger/FK bao ve, suite chi khoi phuc StoreConfig; fixture IT_ duoc giu lai.
 * Nen chay tren DB clone TEST/IT va reset DB test tu snapshot khi can lam sach.
 */
@TestMethodOrder(OrderAnnotation.class)
public class SalesStaffFullFlowIT {

    private static final BigDecimal OPENING_CASH = new BigDecimal("1000000");
    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("10000");

    private static final AuthService AUTH = AuthService.getInstance();
    private static final ShiftService SHIFT_SERVICE = new ShiftService();
    private static final InvoiceDAO INVOICE_DAO = new InvoiceDAO();
    private static final OrderDAO ORDER_DAO = new OrderDAO();
    private static final DashboardDAO DASHBOARD_DAO = new DashboardDAO();

    private static String runTag;
    private static String databaseName;

    private static int staffId;
    private static int managerId;
    private static int customerId;
    private static int productId;
    private static int shiftId;

    private static int splitInvoiceId;
    private static int cancelInvoiceId;
    private static int returnInvoiceId;
    private static int orderId;
    private static Integer onlineInvoiceId;

    private static Long restoredHoldId;
    private static Long cancelledHoldId;

    private static boolean thresholdExisted;
    private static String originalThreshold;

    private static User staffUser;
    private static User managerUser;

    @BeforeAll
    static void beforeAll() throws Exception {
        requireExplicitOptIn();
        preflightDatabase();
        runTag = "IT_" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        seedFixture();
        login(staffUser);
    }

    @AfterAll
    static void afterAll() {
        try {
            AUTH.logout();
        } catch (Exception ignored) {
        }
        try {
            cleanupFixture();
        } catch (Exception e) {
            System.err.println("[SALES_STAFF_IT] Cleanup can kiem tra thu cong: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    @Order(1)
    void openShift_and_cashMovements_work() {
        login(staffUser);

        ShiftService.OperationResult<Shift> opened = SHIFT_SERVICE.openMyShift(OPENING_CASH, runTag + " open shift");
        assertTrue(opened.isSuccess(), opened.getMessage());
        assertNotNull(opened.getData());
        shiftId = opened.getData().getShiftId();
        assertTrue(shiftId > 0);

        ShiftService.OperationResult<ShiftCashTransaction> cashIn = SHIFT_SERVICE.addCashMovement(
                ShiftCashTransaction.CASH_IN, new BigDecimal("20000"), runTag + " cash in");
        assertTrue(cashIn.isSuccess(), cashIn.getMessage());

        ShiftService.OperationResult<ShiftCashTransaction> cashOut = SHIFT_SERVICE.addCashMovement(
                ShiftCashTransaction.CASH_OUT, new BigDecimal("5000"), runTag + " cash out");
        assertTrue(cashOut.isSuccess(), cashOut.getMessage());

        ShiftService.OperationResult<ShiftCashSummary> preview = SHIFT_SERVICE.previewClose();
        assertTrue(preview.isSuccess(), preview.getMessage());
        assertMoney("1015000", preview.getData().getExpectedCash());
    }

    @Test
    @Order(2)
    void posSplitPayment_createsInvoicePaymentLedger_and_scopedDashboard() throws Exception {
        login(staffUser);

        Invoice invoice = new Invoice();
        invoice.setShiftId(shiftId);
        invoice.setCreatedBy(staffId);
        invoice.setPaymentMethod(InvoicePayment.METHOD_CASH); // header legacy; ledger la source of truth
        invoice.setVatRate(BigDecimal.ZERO);
        invoice.setDiscountAmount(BigDecimal.ZERO);

        InvoiceDetail line = new InvoiceDetail();
        line.setProductId(productId);
        line.setQuantity(10);
        line.setUnitPrice(PRODUCT_PRICE);

        InvoicePayment cash = InvoicePayment.cash(new BigDecimal("40000"), new BigDecimal("50000"));
        InvoicePayment card = new InvoicePayment(InvoicePayment.METHOD_CARD, new BigDecimal("60000"));
        card.setProvider("CARD");
        card.setProviderTransactionId(runTag + "-SPLIT-CARD");
        card.setPaymentStatus(InvoicePayment.STATUS_COMPLETED);

        boolean created = INVOICE_DAO.createInvoice(invoice, List.of(line), new BigDecimal("100000"), List.of(cash, card));
        assertTrue(created, "Khong tao duoc hoa don split payment");
        splitInvoiceId = invoice.getInvoiceId();
        assertTrue(splitInvoiceId > 0);
        assertMoney("100000", invoice.getTotalAmount());

        assertEquals(2, queryInt("SELECT COUNT(*) FROM InvoicePayments WHERE InvoiceID=?", splitInvoiceId));
        assertMoney("40000", queryMoney(
                "SELECT COALESCE(SUM(Amount),0) FROM InvoicePayments WHERE InvoiceID=? AND PaymentMethod='CASH' AND PaymentStatus='COMPLETED'",
                splitInvoiceId));
        assertMoney("60000", queryMoney(
                "SELECT COALESCE(SUM(Amount),0) FROM InvoicePayments WHERE InvoiceID=? AND PaymentMethod='CARD' AND PaymentStatus='COMPLETED'",
                splitInvoiceId));
        assertMoney("50000", queryMoney(
                "SELECT TenderedAmount FROM InvoicePayments WHERE InvoiceID=? AND PaymentMethod='CASH'", splitInvoiceId));
        assertMoney("10000", queryMoney(
                "SELECT ChangeAmount FROM InvoicePayments WHERE InvoiceID=? AND PaymentMethod='CASH'", splitInvoiceId));

        assertEquals(90, queryInt("SELECT Stock FROM Products WHERE ProductID=?", productId));

        DashboardDAO.StaffDayStats stats = DASHBOARD_DAO.getStaffShiftStats(staffId, shiftId);
        assertEquals(1, stats.invoiceCount);
        assertMoney("100000", stats.revenue);
        assertEquals(10L, stats.itemsSold);

        ShiftService.OperationResult<ShiftCashSummary> preview = SHIFT_SERVICE.previewClose();
        assertTrue(preview.isSuccess(), preview.getMessage());
        // 1,000,000 opening + 20,000 cash-in - 5,000 cash-out + 40,000 CASH part
        assertMoney("1055000", preview.getData().getExpectedCash());
    }

    @Test
    @Order(3)
    void heldCart_hold_restore_cancel_work_in_sameShift() throws Exception {
        login(staffUser);
        Product product = new ProductDAO().findActiveById(productId);
        assertNotNull(product);

        PosCartService cart = PosCartService.getInstance();
        cart.clear();
        cart.addToCart(product, 2);

        HeldCartService heldService = new HeldCartService();
        HeldCartService.Result<com.model.HeldCart> held = heldService.holdCurrentCart(
                cart, InvoicePayment.METHOD_CASH, runTag + " restore");
        assertTrue(held.isSuccess(), held.getMessage());
        assertTrue(cart.isEmpty());
        restoredHoldId = held.getData().getHoldId();
        assertEquals("HELD", queryString("SELECT Status FROM HeldCarts WHERE HoldID=?", restoredHoldId));

        HeldCartService.Result<com.model.HeldCart> restored = heldService.restoreToCurrentCart(restoredHoldId, cart);
        assertTrue(restored.isSuccess(), restored.getMessage());
        assertFalse(cart.isEmpty());
        assertEquals("RESTORED", queryString("SELECT Status FROM HeldCarts WHERE HoldID=?", restoredHoldId));

        cart.clear();
        product = new ProductDAO().findActiveById(productId);
        cart.addToCart(product, 1);
        HeldCartService.Result<com.model.HeldCart> heldForCancel = heldService.holdCurrentCart(
                cart, InvoicePayment.METHOD_CASH, runTag + " cancel");
        assertTrue(heldForCancel.isSuccess(), heldForCancel.getMessage());
        cancelledHoldId = heldForCancel.getData().getHoldId();

        HeldCartService.Result<com.model.HeldCart> cancelled = heldService.cancel(cancelledHoldId);
        assertTrue(cancelled.isSuccess(), cancelled.getMessage());
        assertEquals("CANCELLED", queryString("SELECT Status FROM HeldCarts WHERE HoldID=?", cancelledHoldId));
        cart.clear();
    }

    @Test
    @Order(4)
    void invoiceCancelRequest_requiresManager_and_approvedRequestCancelsInvoice() throws Exception {
        login(staffUser);
        Invoice invoice = createSimpleCashInvoice(2, runTag + " cancel invoice");
        cancelInvoiceId = invoice.getInvoiceId();

        InvoiceCancelRequestService service = new InvoiceCancelRequestService();
        String requestError = service.requestCancel(cancelInvoiceId, runTag + " cashier entered wrong sale");
        assertNull(requestError, requestError);

        int requestId = queryInt(
                "SELECT RequestID FROM InvoiceCancelRequests WHERE InvoiceID=? ORDER BY RequestID DESC LIMIT 1",
                cancelInvoiceId);
        assertTrue(requestId > 0);
        assertEquals("PENDING", queryString("SELECT Status FROM InvoiceCancelRequests WHERE RequestID=?", requestId));

        login(managerUser);
        String approveError = service.approve(requestId, runTag + " manager approved");
        assertNull(approveError, approveError);

        assertEquals("APPROVED", queryString("SELECT Status FROM InvoiceCancelRequests WHERE RequestID=?", requestId));
        assertEquals("CANCELLED", queryString("SELECT Status FROM Invoices WHERE InvoiceID=?", cancelInvoiceId));
        login(staffUser);
    }

    @Test
    @Order(5)
    void returnUnder500k_autoApproves_withoutManager() throws Exception {
        login(staffUser);
        Invoice invoice = createSimpleCashInvoice(1, runTag + " return invoice");
        returnInvoiceId = invoice.getInvoiceId();

        ReturnExchange header = new ReturnExchange();
        header.setInvoiceId(returnInvoiceId);
        header.setType(ReturnExchange.TYPE_RETURN);
        header.setReason(runTag + " product defect");
        header.setCreatedBy(staffId);
        // CARD de test approval threshold ma khong tao pending cash refund chan dong ca.
        header.setRefundMethod(ReturnExchange.REFUND_CARD);

        ReturnExchangeDetail detail = new ReturnExchangeDetail(productId, 1,
                ReturnExchangeDetail.DIRECTION_IN, PRODUCT_PRICE);
        detail.setProductName(runTag + " product");

        String error = new ReturnExchangeDAO().createReturnExchange(header, List.of(detail));
        assertNull(error, error);
        assertTrue(header.getReturnId() > 0);
        assertFalse(header.isRequiresApproval(), "Phieu 10.000d khong duoc yeu cau duyet voi threshold 500.000d");
        assertEquals(ReturnExchange.STATUS_APPROVED, header.getStatus());
        assertEquals("APPROVED", queryString("SELECT Status FROM ReturnExchanges WHERE ReturnID=?", header.getReturnId()));
    }

    @Test
    @Order(6)
    void assignedOnlineOrder_staffOnlyFlow_recordsHistory_andCreatesInvoice() throws Exception {
        OrderFixture order = createCodOrder(3);
        orderId = order.orderId;

        login(managerUser);
        String assignError = ORDER_DAO.assignOrder(orderId, staffId, managerId);
        assertNull(assignError, assignError);
        assertEquals(staffId, queryInt("SELECT AssignedTo FROM Orders WHERE OrderID=?", orderId));

        login(staffUser);
        OrderDAO.StatusUpdateResult confirmed = ORDER_DAO.updateAssignedOrderStatus(orderId, "CONFIRMED", staffId);
        assertTrue(confirmed.success, confirmed.errorMessage);
        OrderDAO.StatusUpdateResult shipping = ORDER_DAO.updateAssignedOrderStatus(orderId, "SHIPPING", staffId);
        assertTrue(shipping.success, shipping.errorMessage);
        OrderDAO.StatusUpdateResult completed = ORDER_DAO.updateAssignedOrderStatus(orderId, "COMPLETED", staffId);
        assertTrue(completed.success, completed.errorMessage);

        assertEquals("COMPLETED", queryString("SELECT OrderStatus FROM Orders WHERE OrderID=?", orderId));
        assertTrue(queryInt("SELECT COUNT(*) FROM OrderStatusHistory WHERE OrderID=?", orderId) >= 4,
                "Thieu lich su NEW -> CONFIRMED -> SHIPPING -> COMPLETED");

        int linked = queryInt("SELECT COALESCE(InvoiceID,0) FROM Orders WHERE OrderID=?", orderId);
        assertTrue(linked > 0, "Don COD COMPLETED phai co hoa don lien ket");
        onlineInvoiceId = linked;

        // Invariant A12: moi hoa don moi nen co ledger InvoicePayments.
        // Neu assertion nay fail, OrderDAO#createInvoiceForOrder dang bo qua InvoicePayments.
        assertTrue(queryInt("SELECT COUNT(*) FROM InvoicePayments WHERE InvoiceID=?", linked) >= 1,
                "Hoa don tu don online chua co InvoicePayments; can dong bo OrderDAO#createInvoiceForOrder voi A12");
    }

    @Test
    @Order(7)
    void dashboardAndCashSummary_areScopedToCurrentShift() throws Exception {
        login(staffUser);
        DashboardDAO.StaffDayStats stats = DASHBOARD_DAO.getStaffShiftStats(staffId, shiftId);
        assertTrue(stats.invoiceCount >= 2, "Dashboard ca phai thay cac hoa don ACTIVE cua staff trong ca");
        assertTrue(stats.revenue.signum() > 0);

        int assigned = DASHBOARD_DAO.countMyAssignedActiveOrders(staffId);
        assertEquals(0, assigned, "Don test da COMPLETED nen khong con nam trong active assigned count");

        ShiftService.OperationResult<ShiftCashSummary> preview = SHIFT_SERVICE.previewClose();
        assertTrue(preview.isSuccess(), preview.getMessage());
        assertNotNull(preview.getData());
        assertTrue(preview.getData().getExpectedCash().signum() >= 0);
    }

    @Test
    @Order(8)
    void shiftReconciliation_closed_rejected_resubmitted_approved_fullFlow() throws Exception {
        login(staffUser);
        ShiftService.OperationResult<ShiftCashSummary> preview = SHIFT_SERVICE.previewClose();
        assertTrue(preview.isSuccess(), preview.getMessage());

        BigDecimal counted = preview.getData().getExpectedCash();
        ShiftService.OperationResult<Shift> closed = SHIFT_SERVICE.closeMyShift(
                counted, runTag + " blind-count reconciliation");
        assertTrue(closed.isSuccess(), closed.getMessage());
        assertEquals("CLOSED", queryString("SELECT Status FROM Shifts WHERE ShiftID=?", shiftId),
                "Dong ca phai CLOSED ngay, khong dung PENDING_APPROVAL trong Shifts");
        assertTrue(closed.getData().isPendingApproval(), "Doi soat revision 1 phai PENDING");
        assertEquals(1, queryInt("SELECT COUNT(*) FROM ShiftReconciliations WHERE ShiftID=?", shiftId));
        assertEquals("PENDING", queryString(
                "SELECT Status FROM ShiftReconciliations WHERE ShiftID=? ORDER BY RevisionNo DESC LIMIT 1", shiftId));

        login(managerUser);
        ShiftService.OperationResult<Shift> rejected = SHIFT_SERVICE.rejectShift(
                shiftId, runTag + " manager requests recount");
        assertTrue(rejected.isSuccess(), rejected.getMessage());
        assertTrue(rejected.getData().isRejected());
        assertEquals("CLOSED", queryString("SELECT Status FROM Shifts WHERE ShiftID=?", shiftId),
                "Tu choi doi soat khong duoc mo lai ca");
        assertEquals("REJECTED", queryString(
                "SELECT Status FROM ShiftReconciliations WHERE ShiftID=? ORDER BY RevisionNo DESC LIMIT 1", shiftId));

        login(staffUser);
        ShiftService.OperationResult<Shift> resubmitted = SHIFT_SERVICE.resubmitMyReconciliation(
                shiftId, counted, runTag + " recount checked");
        assertTrue(resubmitted.isSuccess(), resubmitted.getMessage());
        assertTrue(resubmitted.getData().isPendingApproval());
        assertEquals("CLOSED", queryString("SELECT Status FROM Shifts WHERE ShiftID=?", shiftId));
        assertEquals(2, queryInt("SELECT COUNT(*) FROM ShiftReconciliations WHERE ShiftID=?", shiftId),
                "Gui lai phai tao revision moi, khong overwrite revision cu");
        assertEquals(2, queryInt(
                "SELECT RevisionNo FROM ShiftReconciliations WHERE ShiftID=? ORDER BY RevisionNo DESC LIMIT 1", shiftId));
        assertEquals("PENDING", queryString(
                "SELECT Status FROM ShiftReconciliations WHERE ShiftID=? ORDER BY RevisionNo DESC LIMIT 1", shiftId));

        login(managerUser);
        ShiftService.OperationResult<Shift> approved = SHIFT_SERVICE.approveShift(
                shiftId, runTag + " approve recount");
        assertTrue(approved.isSuccess(), approved.getMessage());
        assertTrue(approved.getData().isApproved());
        assertEquals("CLOSED", queryString("SELECT Status FROM Shifts WHERE ShiftID=?", shiftId));
        assertEquals("APPROVED", queryString(
                "SELECT Status FROM ShiftReconciliations WHERE ShiftID=? ORDER BY RevisionNo DESC LIMIT 1", shiftId));
    }

    // ---------------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------------

    private static void requireExplicitOptIn() {
        boolean run = flag("SIMS_IT_RUN", "sims.it");
        assertTrue(run,
                "Integration test bi khoa mac dinh. Set SIMS_IT_RUN=true (hoac -Dsims.it=true) roi chay lai.");
    }

    private static void preflightDatabase() throws Exception {
        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                assertTrue(rs.next());
                databaseName = rs.getString(1);
            }
        }
        assertNotNull(databaseName);
        String lower = databaseName.toLowerCase(Locale.ROOT);
        boolean looksLikeTestDb = lower.contains("test") || lower.endsWith("_it") || lower.startsWith("it_");
        boolean allowShared = flag("SIMS_IT_ALLOW_SHARED_DB", "sims.it.allowSharedDb");
        assertTrue(looksLikeTestDb || allowShared,
                "Dang tro vao database '" + databaseName + "'. Hay dung database clone co ten TEST/IT, "
                + "hoac CHI khi chap nhan du lieu test tam thoi thi set SIMS_IT_ALLOW_SHARED_DB=true.");

        assertTable("InvoicePayments");
        assertTable("HeldCarts");
        assertTable("OrderStatusHistory");
        assertTable("InvoiceCancelRequests");
        assertTable("ReturnExchangeEvidence");
        assertTable("ShiftReconciliations");
    }

    private static void seedFixture() throws Exception {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                staffId = insertUser(con, "SALES_STAFF", "staff");
                managerId = insertUser(con, "SALES_MANAGER", "manager");
                customerId = insertUser(con, "CUSTOMER", "customer");
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO Customers (CustomerID,CustomerCode,MemberPoint) VALUES (?,?,0)")) {
                    ps.setInt(1, customerId);
                    ps.setString(2, "CUS_" + runTag.substring(Math.max(0, runTag.length() - 10)));
                    ps.executeUpdate();
                }

                int categoryId = scalarInt(con,
                        "SELECT CategoryID FROM Categories WHERE Status='ACTIVE' ORDER BY CategoryID LIMIT 1");
                int supplierId = scalarInt(con,
                        "SELECT SupplierID FROM Suppliers WHERE IsDeleted=0 ORDER BY SupplierID LIMIT 1");
                assertTrue(categoryId > 0, "Can it nhat 1 Category ACTIVE trong DB test");
                assertTrue(supplierId > 0, "Can it nhat 1 Supplier trong DB test");

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO Products (ProductCode,ProductName,CategoryID,ImportPrice,SellPrice,Stock,MinStock,Status) "
                                + "VALUES (?,?,?,?,?,100,0,'ACTIVE')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "IT" + Long.toString(System.nanoTime(), 36).toUpperCase(Locale.ROOT));
                    ps.setString(2, runTag + " Integration Product");
                    ps.setInt(3, categoryId);
                    ps.setBigDecimal(4, new BigDecimal("8000"));
                    ps.setBigDecimal(5, PRODUCT_PRICE);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        assertTrue(keys.next());
                        productId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO InventoryBatch (BatchCode,LotNumber,ProductID,SupplierID,ExpiryDate,ImportPrice,Quantity,RemainingQty,Status) "
                                + "VALUES (?,?,?,?,?,?,?,?, 'ACTIVE')")) {
                    ps.setString(1, "ITB" + Long.toString(System.nanoTime(), 36).toUpperCase(Locale.ROOT));
                    ps.setString(2, runTag + "-LOT");
                    ps.setInt(3, productId);
                    ps.setInt(4, supplierId);
                    ps.setObject(5, LocalDate.now().plusYears(1));
                    ps.setBigDecimal(6, new BigDecimal("8000"));
                    ps.setInt(7, 100);
                    ps.setInt(8, 100);
                    ps.executeUpdate();
                }

                originalThreshold = null;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT ConfigValue FROM StoreConfig WHERE ConfigKey='RETURN_APPROVAL_THRESHOLD'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            thresholdExisted = true;
                            originalThreshold = rs.getString(1);
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO StoreConfig (ConfigKey,ConfigValue) VALUES ('RETURN_APPROVAL_THRESHOLD','500000') "
                                + "ON DUPLICATE KEY UPDATE ConfigValue='500000'")) {
                    ps.executeUpdate();
                }

                con.commit();
            } catch (Throwable t) {
                con.rollback();
                throw t;
            }
        }

        staffUser = testUser(staffId, "SALES_STAFF", "IT Staff");
        managerUser = testUser(managerId, "SALES_MANAGER", "IT Manager");
    }

    private static int insertUser(Connection con, String roleCode, String suffix) throws SQLException {
        int roleId = scalarInt(con, "SELECT RoleID FROM Roles WHERE RoleCode='" + roleCode + "'");
        assertTrue(roleId > 0, "Khong tim thay role " + roleCode);
        String username = (runTag + "_" + suffix).toLowerCase(Locale.ROOT);
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Users (Username,PasswordHash,FullName,Email,RoleID,Status,IsLocked,FailedLoginCount,IsDeleted) "
                        + "VALUES (?,?,?,?,?,'ACTIVE',0,0,0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "IT-NOT-FOR-LOGIN");
            ps.setString(3, runTag + " " + suffix);
            ps.setString(4, username + "@example.invalid");
            ps.setInt(5, roleId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static User testUser(int id, String roleCode, String name) {
        User u = new User();
        u.setUserId(id);
        u.setUsername(runTag.toLowerCase(Locale.ROOT) + "_" + roleCode.toLowerCase(Locale.ROOT));
        u.setFullName(name);
        u.setRoleCode(roleCode);
        u.setStatus("ACTIVE");
        return u;
    }

    private static void login(User user) {
        AUTH.setCurrentUser(user);
    }

    private static Invoice createSimpleCashInvoice(int qty, String note) {
        Invoice invoice = new Invoice();
        invoice.setShiftId(shiftId);
        invoice.setCreatedBy(staffId);
        invoice.setPaymentMethod(InvoicePayment.METHOD_CASH);
        invoice.setVatRate(BigDecimal.ZERO);
        invoice.setDiscountAmount(BigDecimal.ZERO);

        InvoiceDetail detail = new InvoiceDetail();
        detail.setProductId(productId);
        detail.setQuantity(qty);
        detail.setUnitPrice(PRODUCT_PRICE);

        BigDecimal total = PRODUCT_PRICE.multiply(BigDecimal.valueOf(qty));
        InvoicePayment cash = InvoicePayment.cash(total, total);
        boolean ok = INVOICE_DAO.createInvoice(invoice, List.of(detail), total, List.of(cash));
        assertTrue(ok, "Khong tao duoc hoa don fixture: " + note);
        return invoice;
    }

    private static OrderFixture createCodOrder(int qty) {
        com.model.Order order = new com.model.Order();
        BigDecimal sub = PRODUCT_PRICE.multiply(BigDecimal.valueOf(qty));
        BigDecimal total = sub.multiply(new BigDecimal("1.08")).setScale(0, java.math.RoundingMode.HALF_UP);
        order.setCustomerId(customerId);
        order.setCustomerName(runTag + " Customer");
        order.setCustomerEmail(runTag.toLowerCase(Locale.ROOT) + "@example.invalid");
        order.setCustomerPhone("0900000000");
        order.setShippingAddress("Integration Test Address");
        order.setSubTotal(sub);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(total);
        order.setPaymentMethod("COD");
        order.setPaymentStatus("PENDING");

        OrderDetail detail = new OrderDetail(productId, runTag + " Integration Product", qty, PRODUCT_PRICE);
        boolean ok = ORDER_DAO.createOrder(order, List.of(detail));
        assertTrue(ok, "Khong tao duoc don online fixture");
        return new OrderFixture(order.getOrderId());
    }

    private record OrderFixture(int orderId) {}

    // ---------------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------------

    private static void cleanupFixture() throws Exception {
        if (staffId <= 0 && managerId <= 0 && customerId <= 0 && productId <= 0) return;

        // Hoa don trong SIMS la du lieu nghiep vu bat bien: DB/trigger co y khong cho
        // DELETE vinh vien. Vi vay integration test khong co gang xoa Invoices/Shifts/
        // Users bang cascade/disable FK. Lam vay se bien cleanup thanh mot hanh vi nguy
        // hiem va trai voi invariant production.
        //
        // Suite chi khoi phuc StoreConfig da tam thay doi. Cac fixture con lai deu co
        // prefix IT_ va nen chay tren DB clone TEST/IT; khi can lam sach, reset DB test
        // tu snapshot thay vi pha trigger/FK.
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (thresholdExisted) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE StoreConfig SET ConfigValue=? WHERE ConfigKey='RETURN_APPROVAL_THRESHOLD'")) {
                        ps.setString(1, originalThreshold);
                        ps.executeUpdate();
                    }
                } else {
                    exec(con, "DELETE FROM StoreConfig WHERE ConfigKey='RETURN_APPROVAL_THRESHOLD'");
                }
                con.commit();
            } catch (Throwable t) {
                con.rollback();
                throw t;
            } finally {
                con.setAutoCommit(true);
            }
        }

        System.out.println("[SALES_STAFF_IT cleanup] Da khoi phuc StoreConfig. "
                + "Fixture IT_ duoc giu lai vi Invoices la immutable; hay reset DB TEST/IT neu can lam sach.");
    }

    // ---------------------------------------------------------------------
    // SQL/assert helpers
    // ---------------------------------------------------------------------

    private static void assertTable(String table) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SHOW TABLES LIKE ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Thieu table " + table + ". Chay migration moi nhat truoc integration test.");
            }
        }
    }

    private static int queryInt(String sql, Object... params) throws SQLException {
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Query khong tra ve dong nao: " + sql);
                return rs.getInt(1);
            }
        }
    }

    private static BigDecimal queryMoney(String sql, Object... params) throws SQLException {
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Query khong tra ve dong nao: " + sql);
                BigDecimal value = rs.getBigDecimal(1);
                return value != null ? value : BigDecimal.ZERO;
            }
        }
    }

    private static String queryString(String sql, Object... params) throws SQLException {
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Query khong tra ve dong nao: " + sql);
                return rs.getString(1);
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    private static int scalarInt(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void exec(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            // Cleanup phai co gang tiep tuc voi bang tuy chon / du lieu khong ton tai.
            System.err.println("[SALES_STAFF_IT cleanup] " + e.getMessage() + " | SQL=" + sql);
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "Expected " + expected + " but got " + actual.toPlainString());
    }

    private static boolean flag(String envName, String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) value = System.getenv(envName);
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim())
                || "yes".equalsIgnoreCase(value.trim()));
    }
}
