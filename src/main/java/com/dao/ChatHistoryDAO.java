package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.chat.ChatConversation;
import com.model.chat.ChatHistoryMessage;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lưu / đọc lịch sử chat khách–NV và NV–NV.
 * Không liên quan chatbot AI.
 */
public class ChatHistoryDAO {

    /** Tìm hoặc tạo hội thoại hỗ trợ của 1 khách. */
    public ChatConversation findOrCreateCustomerSupport(int customerUserId) {
        String select = "SELECT TOP 1 * FROM ChatConversations "
                + "WHERE ConversationType = 'CUSTOMER_SUPPORT' AND CustomerUserID = ? AND IsClosed = 0 "
                + "ORDER BY ConversationID DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(select)) {
            ps.setInt(1, customerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapConversation(rs);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ChatHistoryDAO.findOrCreateCustomerSupport select - customer=" + customerUserId, e);
        }

        String insert = "INSERT INTO ChatConversations (ConversationType, CustomerUserID) VALUES ('CUSTOMER_SUPPORT', ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerUserId);
            if (ps.executeUpdate() == 0) return null;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return null;
                ChatConversation c = new ChatConversation();
                c.setConversationId(keys.getInt(1));
                c.setConversationType(ChatConversation.TYPE_CUSTOMER_SUPPORT);
                c.setCustomerUserId(customerUserId);
                c.setCreatedAt(LocalDateTime.now());
                c.setLastMessageAt(LocalDateTime.now());
                return c;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ChatHistoryDAO.findOrCreateCustomerSupport insert - customer=" + customerUserId, e);
            // race unique index → select lại
            return findCustomerSupport(customerUserId);
        }
    }

    private ChatConversation findCustomerSupport(int customerUserId) {
        String select = "SELECT TOP 1 * FROM ChatConversations "
                + "WHERE ConversationType = 'CUSTOMER_SUPPORT' AND CustomerUserID = ? AND IsClosed = 0 "
                + "ORDER BY ConversationID DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(select)) {
            ps.setInt(1, customerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapConversation(rs) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Tìm hoặc tạo DM giữa 2 nhân viên (thứ tự userId không quan trọng). */
    public ChatConversation findOrCreateStaffDm(int userId1, int userId2) {
        if (userId1 == userId2) return null;
        int a = Math.min(userId1, userId2);
        int b = Math.max(userId1, userId2);

        String select = "SELECT TOP 1 * FROM ChatConversations "
                + "WHERE ConversationType = 'STAFF_DM' AND StaffUserIdA = ? AND StaffUserIdB = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(select)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapConversation(rs);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ChatHistoryDAO.findOrCreateStaffDm select", e);
        }

        String insert = "INSERT INTO ChatConversations (ConversationType, StaffUserIdA, StaffUserIdB) "
                + "VALUES ('STAFF_DM', ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            if (ps.executeUpdate() == 0) return null;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return null;
                ChatConversation c = new ChatConversation();
                c.setConversationId(keys.getInt(1));
                c.setConversationType(ChatConversation.TYPE_STAFF_DM);
                c.setStaffUserIdA(a);
                c.setStaffUserIdB(b);
                c.setCreatedAt(LocalDateTime.now());
                c.setLastMessageAt(LocalDateTime.now());
                return c;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ChatHistoryDAO.findOrCreateStaffDm insert", e);
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(select)) {
                ps.setInt(1, a);
                ps.setInt(2, b);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapConversation(rs) : null;
                }
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public long insertMessage(int conversationId, int senderUserId, String senderName,
                              boolean fromStaff, String bodyText, String imagePath, String imageMime,
                              String filePath, String fileName) {
        String sql = "INSERT INTO ChatMessages "
                + "(ConversationID, SenderUserID, SenderName, FromStaff, BodyText, ImagePath, ImageMime, FilePath, FileName) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, conversationId);
            ps.setInt(2, senderUserId);
            ps.setString(3, senderName != null ? senderName : "");
            ps.setBoolean(4, fromStaff);
            ps.setString(5, bodyText);
            ps.setString(6, imagePath);
            ps.setString(7, imageMime);
            ps.setString(8, filePath);
            ps.setString(9, fileName);
            if (ps.executeUpdate() == 0) return -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1;
                touchConversation(conversationId);
                return id;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ChatHistoryDAO.insertMessage - conv=" + conversationId, e);
            return -1;
        }
    }

    private void touchConversation(int conversationId) {
        String sql = "UPDATE ChatConversations SET LastMessageAt = GETDATE() WHERE ConversationID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            ps.executeUpdate();
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "ChatHistoryDAO.touchConversation - " + conversationId, e);
        }
    }

    /** Lịch sử theo conversation, cũ → mới, tối đa limit tin gần nhất rồi sort lại. */
    public List<ChatHistoryMessage> listMessages(int conversationId, int limit) {
        int lim = Math.max(1, Math.min(limit, 500));
        String sql = "SELECT * FROM ("
                + "  SELECT TOP (" + lim + ") * FROM ChatMessages "
                + "  WHERE ConversationID = ? ORDER BY CreatedAt DESC, MessageID DESC"
                + ") t ORDER BY CreatedAt ASC, MessageID ASC";
        List<ChatHistoryMessage> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapMessage(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ChatHistoryDAO.listMessages - conv=" + conversationId, e);
        }
        return list;
    }

    public List<ChatHistoryMessage> listCustomerSupportHistory(int customerUserId, int limit) {
        ChatConversation c = findCustomerSupport(customerUserId);
        if (c == null) return List.of();
        return listMessages(c.getConversationId(), limit);
    }

    /** Xóa 1 tin theo MessageID. Trả về true nếu có dòng bị xóa. */
    public boolean deleteMessage(long messageId) {
        if (messageId <= 0) return false;
        String sql = "DELETE FROM ChatMessages WHERE MessageID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "ChatHistoryDAO.deleteMessage - id=" + messageId, e);
            return false;
        }
    }

    /** Xóa toàn bộ tin trong 1 conversation. */
    public int deleteAllMessages(int conversationId) {
        if (conversationId <= 0) return 0;
        String sql = "DELETE FROM ChatMessages WHERE ConversationID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, conversationId);
            return ps.executeUpdate();
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "ChatHistoryDAO.deleteAllMessages - conv=" + conversationId, e);
            return 0;
        }
    }

    /** Xóa toàn bộ tin hội thoại hỗ trợ của 1 khách. */
    public int deleteCustomerSupportMessages(int customerUserId) {
        ChatConversation c = findCustomerSupport(customerUserId);
        if (c == null) return 0;
        return deleteAllMessages(c.getConversationId());
    }

    /** Xóa toàn bộ tin DM giữa 2 nhân viên. */
    public int deleteStaffDmMessages(int userId1, int userId2) {
        int a = Math.min(userId1, userId2);
        int b = Math.max(userId1, userId2);
        String select = "SELECT TOP 1 ConversationID FROM ChatConversations "
                + "WHERE ConversationType = 'STAFF_DM' AND StaffUserIdA = ? AND StaffUserIdB = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(select)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                return deleteAllMessages(rs.getInt(1));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ChatHistoryDAO.deleteStaffDmMessages", e);
            return 0;
        }
    }

    public List<ChatHistoryMessage> listStaffDmHistory(int userId1, int userId2, int limit) {
        int a = Math.min(userId1, userId2);
        int b = Math.max(userId1, userId2);
        String select = "SELECT TOP 1 ConversationID FROM ChatConversations "
                + "WHERE ConversationType = 'STAFF_DM' AND StaffUserIdA = ? AND StaffUserIdB = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(select)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return List.of();
                return listMessages(rs.getInt(1), limit);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ChatHistoryDAO.listStaffDmHistory", e);
            return List.of();
        }
    }

    /** Danh sách hội thoại hỗ trợ gần đây (cho admin inbox). */
    public List<ChatConversation> listRecentCustomerSupport(int limit) {
        int lim = Math.max(1, Math.min(limit, 100));
        String sql = "SELECT TOP (" + lim + ") * FROM ChatConversations "
                + "WHERE ConversationType = 'CUSTOMER_SUPPORT' "
                + "ORDER BY LastMessageAt DESC";
        List<ChatConversation> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapConversation(rs));
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ChatHistoryDAO.listRecentCustomerSupport", e);
        }
        return list;
    }

    private ChatConversation mapConversation(ResultSet rs) throws java.sql.SQLException {
        ChatConversation c = new ChatConversation();
        c.setConversationId(rs.getInt("ConversationID"));
        c.setConversationType(rs.getString("ConversationType"));
        int cu = rs.getInt("CustomerUserID");
        c.setCustomerUserId(rs.wasNull() ? null : cu);
        int a = rs.getInt("StaffUserIdA");
        c.setStaffUserIdA(rs.wasNull() ? null : a);
        int b = rs.getInt("StaffUserIdB");
        c.setStaffUserIdB(rs.wasNull() ? null : b);
        Timestamp ca = rs.getTimestamp("CreatedAt");
        if (ca != null) c.setCreatedAt(ca.toLocalDateTime());
        Timestamp la = rs.getTimestamp("LastMessageAt");
        if (la != null) c.setLastMessageAt(la.toLocalDateTime());
        c.setClosed(rs.getBoolean("IsClosed"));
        return c;
    }

    private ChatHistoryMessage mapMessage(ResultSet rs) throws java.sql.SQLException {
        ChatHistoryMessage m = new ChatHistoryMessage();
        m.setMessageId(rs.getLong("MessageID"));
        m.setConversationId(rs.getInt("ConversationID"));
        m.setSenderUserId(rs.getInt("SenderUserID"));
        m.setSenderName(rs.getString("SenderName"));
        m.setFromStaff(rs.getBoolean("FromStaff"));
        m.setBodyText(rs.getString("BodyText"));
        m.setImagePath(rs.getString("ImagePath"));
        m.setImageMime(rs.getString("ImageMime"));
        try {
            m.setFilePath(rs.getString("FilePath"));
            m.setFileName(rs.getString("FileName"));
        } catch (Exception ignored) {
            // Cot chua co neu DB chua ALTER
        }
        Timestamp ts = rs.getTimestamp("CreatedAt");
        if (ts != null) m.setCreatedAt(ts.toLocalDateTime());
        m.setReadByPeer(rs.getBoolean("IsReadByPeer"));
        return m;
    }
}
