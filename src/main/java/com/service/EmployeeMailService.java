package com.service;

import com.service.mail.MailSender;

import jakarta.mail.MessagingException;

/**
 * Gui email thong bao tai khoan cho nhan vien vua duoc Admin tao qua trang
 * Quan ly nhan vien: mat khau ngau nhien (xem {@link com.utils.RandomPasswordGenerator})
 * CHI duoc gui 1 lan qua email nay - KHONG bao gio luu plain-text xuong DB
 * hay ghi ra log, dung nhu quy uoc bao mat cua UserDAO/PasswordUtils hien co.
 */
public class EmployeeMailService {

    private final MailSender mailSender = new MailSender();

    public void sendCredentials(String toEmail, String fullName, String employeeCode,
                                 String username, String rawPassword) throws MessagingException {
        String subject = "Tài khoản nhân viên của bạn tại SIMS";
        String body =
            "Chào " + fullName + ",\n\n" +
            "Tài khoản nhân viên của bạn vừa được tạo trên hệ thống SIMS với thông tin sau:\n\n" +
            "  Mã nhân viên:     " + employeeCode + "\n" +
            "  Tên đăng nhập:    " + username + "\n" +
            "  Mật khẩu tạm thời: " + rawPassword + "\n\n" +
            "Vui lòng đăng nhập và đổi mật khẩu ngay trong lần đăng nhập đầu tiên.\n" +
            "Không chia sẻ thông tin này cho bất kỳ ai.\n\n" +
            "Trân trọng,\nSIMS";

        mailSender.send(toEmail, subject, body);
    }
}