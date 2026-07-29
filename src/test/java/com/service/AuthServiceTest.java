package com.service;

import com.dao.UserDAO;
import com.model.Role;
import com.model.User;
import com.utils.DBConnection;
import com.utils.PasswordUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luong dang nhap.
 *
 * LUU Y VE KIEN TRUC: {@link AuthService} chi la noi LUU TRANG THAI phien dang
 * nhap hien tai (currentUser) - no khong tu kiem tra username/password/khoa/vo
 * hieu hoa. Logic quyet dinh dang nhap thanh cong hay khong nam trong
 * {@link UserDAO#login(String, String)} (truy van DB qua JDBC).
 *
 * Vi vay bo test nay:
 *  1) Goi UserDAO.login(...) that (khong sua code san xuat) nhung "gia lap"
 *     tang JDBC (Connection/PreparedStatement/ResultSet) bang Mockito, de
 *     KHONG can ket noi CSDL that khi chay test (test nhanh, on dinh, lap lai
 *     duoc nhieu lan).
 *  2) Xac nhan ket qua tra ve dung/sai cho tung tinh huong, DONG THOI xac
 *     nhan AuthService phan anh dung trang thai dang nhap (setCurrentUser chi
 *     duoc goi/ap dung khi dang nhap thanh cong).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String VALID_PASSWORD = "Password@123";
    private static final String WRONG_PASSWORD = "WrongPass@123";

    @Mock
    private Connection connectionMock;
    @Mock
    private PreparedStatement selectStatementMock;
    @Mock
    private PreparedStatement updateStatementMock;
    @Mock
    private ResultSet resultSetMock;

    private MockedStatic<DBConnection> dbConnectionStaticMock;
    private UserDAO userDAO;
    private String validPasswordHash;

    @BeforeEach
    void setUp() throws SQLException {
        userDAO = new UserDAO();
        // Hash that (BCrypt) cho mat khau dung, dung de PasswordUtils.verify()
        // chay that trong luc test - khong mock ham nay vi day chinh la logic
        // can kiem tra (sai mat khau phai bi tu choi that su).
        validPasswordHash = PasswordUtils.hash(VALID_PASSWORD);

        // Chan DBConnection.getConnection() (static) de tra ve Connection gia,
        // thay vi ket noi SQL Server that.
        dbConnectionStaticMock = Mockito.mockStatic(DBConnection.class);
        dbConnectionStaticMock.when(DBConnection::getConnection).thenReturn(connectionMock);

        // UserDAO.login() dung 1 cau SELECT de doc thong tin user, va cac ham
        // resetFailedLogin()/registerFailedLogin() dung cau UPDATE rieng ->
        // phan biet 2 loai PreparedStatement theo noi dung SQL.
        lenient().when(connectionMock.prepareStatement(argThat(sql -> sql != null && sql.trim().startsWith("SELECT"))))
                .thenReturn(selectStatementMock);
        lenient().when(connectionMock.prepareStatement(argThat(sql -> sql != null && sql.trim().startsWith("UPDATE"))))
                .thenReturn(updateStatementMock);

        lenient().when(selectStatementMock.executeQuery()).thenReturn(resultSetMock);
        lenient().when(updateStatementMock.executeUpdate()).thenReturn(1);

        // Dam bao khong test nao bi anh huong boi trang thai dang nhap cua
        // test truoc do (AuthService la Singleton dung chung).
        AuthService.getInstance().logout();
    }

    @AfterEach
    void tearDown() {
        AuthService.getInstance().logout();
        dbConnectionStaticMock.close();
    }

    /** Gia lap 1 dong ket qua tra ve tu bang Users trong SELECT. */
    private void stubUserRow(boolean isLocked, String status, String passwordHash, int failedLoginCount) throws SQLException {
        when(resultSetMock.next()).thenReturn(true);
        when(resultSetMock.getBoolean("IsLocked")).thenReturn(isLocked);
        when(resultSetMock.getString("Status")).thenReturn(status);
        when(resultSetMock.getInt("UserID")).thenReturn(1);
        lenient().when(resultSetMock.getString("PasswordHash")).thenReturn(passwordHash);
        lenient().when(resultSetMock.getInt("FailedLoginCount")).thenReturn(failedLoginCount);
        lenient().when(resultSetMock.getString("Username")).thenReturn("nguyenvana");
        lenient().when(resultSetMock.getString("FullName")).thenReturn("Nguyen Van A");
        lenient().when(resultSetMock.getString("Email")).thenReturn("a@example.com");
        lenient().when(resultSetMock.getString("Phone")).thenReturn("0900000000");
        lenient().when(resultSetMock.getString("AvatarUrl")).thenReturn(null);
        lenient().when(resultSetMock.getString("RoleCode")).thenReturn(Role.SALES_STAFF.name());
        lenient().when(resultSetMock.getTimestamp("CreatedAt")).thenReturn(null);
    }

    @Test
    @DisplayName("1) Username dung + password dung -> dang nhap thanh cong")
    void login_withCorrectUsernameAndPassword_shouldSucceedAndAuthServiceStoresUser() throws SQLException {
        stubUserRow(false, "ACTIVE", validPasswordHash, 0);

        User result = userDAO.login("nguyenvana", VALID_PASSWORD);

        assertNotNull(result, "Dang nhap dung thi phai tra ve User, khong duoc null");
        assertEquals("nguyenvana", result.getUsername());
        assertEquals(Role.SALES_STAFF, result.getRole());

        // Mo phong buoc LoginFrame goi sau khi login() thanh cong.
        AuthService.getInstance().setCurrentUser(result);

        assertTrue(AuthService.getInstance().isLoggedIn());
        assertEquals("nguyenvana", AuthService.getInstance().getCurrentUser().getUsername());

        // FailedLoginCount phai duoc reset ve 0 khi dang nhap thanh cong.
        verify(updateStatementMock, atLeastOnce()).executeUpdate();
    }

    @Test
    @DisplayName("2) Sai password -> dang nhap that bai, AuthService khong luu phien")
    void login_withWrongPassword_shouldFail() throws SQLException {
        stubUserRow(false, "ACTIVE", validPasswordHash, 0);

        User result = userDAO.login("nguyenvana", WRONG_PASSWORD);

        assertNull(result, "Sai mat khau thi phai tra ve null");
        assertFalse(AuthService.getInstance().isLoggedIn());

        // FailedLoginCount phai duoc tang len (registerFailedLogin).
        verify(updateStatementMock, atLeastOnce()).executeUpdate();
    }

    @Test
    @DisplayName("3) Tai khoan bi khoa (IsLocked = true) -> dang nhap that bai du password dung")
    void login_withLockedAccount_shouldFail() throws SQLException {
        stubUserRow(true, "ACTIVE", validPasswordHash, 5);

        User result = userDAO.login("nguyenvana", VALID_PASSWORD);

        assertNull(result, "Tai khoan bi khoa thi phai tra ve null du dung mat khau");
        assertFalse(AuthService.getInstance().isLoggedIn());
        // Tai khoan bi khoa: login() return null NGAY, khong dung UPDATE nao.
        verify(updateStatementMock, never()).executeUpdate();
    }

    @Test
    @DisplayName("4) Tai khoan bi vo hieu hoa (Status = DISABLED) -> dang nhap that bai du password dung")
    void login_withDisabledAccount_shouldFail() throws SQLException {
        stubUserRow(false, "DISABLED", validPasswordHash, 0);

        User result = userDAO.login("nguyenvana", VALID_PASSWORD);

        assertNull(result, "Tai khoan bi vo hieu hoa thi phai tra ve null du dung mat khau");
        assertFalse(AuthService.getInstance().isLoggedIn());
        verify(updateStatementMock, never()).executeUpdate();
    }

    @Test
    @DisplayName("5) Username khong ton tai -> dang nhap that bai")
    void login_withNonExistentUsername_shouldFail() throws SQLException {
        when(resultSetMock.next()).thenReturn(false);

        User result = userDAO.login("khongtontai", VALID_PASSWORD);

        assertNull(result, "Username khong ton tai thi phai tra ve null");
        assertFalse(AuthService.getInstance().isLoggedIn());
        verify(updateStatementMock, never()).executeUpdate();
    }
    
    @Test
    @DisplayName("6) Username da ton tai -> phat hien trung username")
    void duplicateUsername_shouldBeDetected() throws SQLException {
        // Gia lap database tim thay username nay
        when(resultSetMock.next()).thenReturn(true);

        boolean result = userDAO.usernameExists("nguyenvana");

        assertTrue(result,
                "Username da ton tai thi phai duoc phat hien la trung");

        verify(selectStatementMock).setString(1, "nguyenvana");
        verify(selectStatementMock).executeQuery();
    }

    @Test
    @DisplayName("7) Email da ton tai -> phat hien trung email")
    void duplicateEmail_shouldBeDetected() throws SQLException {
        // Gia lap database tim thay email nay
        when(resultSetMock.next()).thenReturn(true);

        boolean result = userDAO.emailExists("a@example.com");

        assertTrue(result,
                "Email da ton tai thi phai duoc phat hien la trung");

        verify(selectStatementMock).setString(1, "a@example.com");
        verify(selectStatementMock).executeQuery();
    }
}
