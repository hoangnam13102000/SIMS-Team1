package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.ReturnExchangeEvidence;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ReturnExchangeEvidenceDAO {

    public boolean add(int returnId, String imageUrl, String originalFileName, int uploadedBy) {
        String sql = "INSERT INTO ReturnExchangeEvidence(ReturnID, ImageUrl, OriginalFileName, UploadedBy) VALUES(?,?,?,?)";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            ps.setString(2, imageUrl);
            ps.setString(3, originalFileName);
            ps.setInt(4, uploadedBy);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "ReturnExchangeEvidenceDAO.add - returnId=" + returnId, e);
            return false;
        }
    }

    public List<ReturnExchangeEvidence> getByReturnId(int returnId) {
        String sql = "SELECT e.EvidenceID, e.ReturnID, e.ImageUrl, e.OriginalFileName, e.UploadedBy, "
                + "u.FullName AS UploadedByName, e.UploadedAt "
                + "FROM ReturnExchangeEvidence e JOIN Users u ON u.UserID=e.UploadedBy "
                + "WHERE e.ReturnID=? ORDER BY e.EvidenceID";
        List<ReturnExchangeEvidence> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReturnExchangeEvidence e = new ReturnExchangeEvidence();
                    e.setEvidenceId(rs.getLong("EvidenceID"));
                    e.setReturnId(rs.getInt("ReturnID"));
                    e.setImageUrl(rs.getString("ImageUrl"));
                    e.setOriginalFileName(rs.getString("OriginalFileName"));
                    e.setUploadedBy(rs.getInt("UploadedBy"));
                    e.setUploadedByName(rs.getString("UploadedByName"));
                    Timestamp ts = rs.getTimestamp("UploadedAt");
                    e.setUploadedAt(ts != null ? ts.toLocalDateTime() : null);
                    list.add(e);
                }
            }
        } catch (SQLException ex) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ReturnExchangeEvidenceDAO.getByReturnId - returnId=" + returnId, ex);
        }
        return list;
    }
}
