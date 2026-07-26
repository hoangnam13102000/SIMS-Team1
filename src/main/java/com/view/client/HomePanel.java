package com.view.client;

import com.theme.AppColor;
import com.theme.AppFont;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Trang chu MAU cua framework (phia khach hang) - rong, chi de minh hoa
 * cach 1 trang duoc ghep vao ClientMainFrame qua CardLayout + ClientHeader.
 * Thay noi dung ben trong bang trang nghiep vu that cua ban (vd danh sach
 * san pham, tin tuc...).
 */
public class HomePanel extends JPanel {

    public HomePanel() {
        setLayout(new GridBagLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel label = new JLabel("Trang chủ (mẫu) - thêm nội dung của bạn ở đây");
        label.setFont(AppFont.HEADING_MD);
        label.setForeground(AppColor.TEXT_MUTED);
        add(label);
    }
}
