package com.view.admin.auditlog;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.model.ActivityLog;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dialog CHI XEM chi tiết 1 dòng audit log — header badge theo loại hành động,
 * meta info dạng card, và phần "Những gì đã thay đổi" hiển thị dạng so sánh
 * (diff) dễ đọc thay vì 2 khối JSON thô, để cả người không rành kỹ thuật
 * cũng hiểu được trường nào đã bị đổi và đổi từ giá trị gì sang giá trị gì.
 * Vẫn giữ 1 nút "Xem JSON gốc" cho người cần xem dữ liệu thô (dev/QA).
 */
final class AuditLogDetailDialog extends JDialog {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("#,##0.###");

    /** Các field mang tính chất tiền/số lượng — canh phải + format nghìn khi hiển thị. */
    private static final Set<String> NUMERIC_HINT_KEYS = Set.of(
            "price", "amount", "salary", "debt", "value", "total", "vat", "margin",
            "stock", "quantity", "qty", "point", "balance"
    );

    /**
     * Từ điển nhãn tiếng Việt cho các field JSON hay gặp trong snapshot audit log.
     * Key luôn viết thường, không dấu phân cách (so khớp bất kể camelCase hay PascalCase).
     */
    private static final Map<String, String> FIELD_LABELS = buildFieldLabels();

    private CardLayout snapshotCards;
    private JPanel snapshotCardPanel;
    private JButton toggleRawBtn;
    private boolean showingRaw = false;

    private AuditLogDetailDialog(
            Window owner,
            ActivityLog log,
            String actionLabel,
            String entityLabel
    ) {

        super(owner, "Chi tiết nhật ký", ModalityType.APPLICATION_MODAL);

        setSize(760, 660);
        setMinimumSize(new Dimension(600, 480));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        add(buildHeader(log, actionLabel), BorderLayout.NORTH);
        add(buildBody(log, actionLabel, entityLabel), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    // Header: icon badge + title + action pill
    // ------------------------------------------------------------------

    private JPanel buildHeader(ActivityLog log, String actionLabel) {

        Color accent = actionColor(log.getAction());
        FontAwesomeSolid icon = actionIcon(log.getAction());

        JPanel header = new JPanel(
                new BorderLayout(AppSpacing.MD, 0)
        );

        header.setOpaque(true);
        header.setBackground(AppColor.WHITE);

        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                        0, 0, 1, 0, AppColor.BORDER
                ),
                new EmptyBorder(
                        AppSpacing.LG,
                        AppSpacing.XL,
                        AppSpacing.LG,
                        AppSpacing.XL
                )
        ));

        JLabel iconLabel = new JLabel(
                FontIcon.of(icon, 18, accent)
        );

        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(44, 44));
        iconLabel.setOpaque(true);
        iconLabel.setBackground(soft(accent, 28));
        iconLabel.setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Chi tiết nhật ký");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(
                actionLabel != null ? actionLabel : "—"
        );

        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(title);
        textCol.add(Box.createVerticalStrut(3));
        textCol.add(subtitle);

        JLabel badge = new JLabel(
                actionLabel != null
                        ? actionLabel.toUpperCase()
                        : "—"
        );

        badge.setFont(AppFont.SMALL_BOLD);
        badge.setForeground(accent);
        badge.setOpaque(true);
        badge.setBackground(soft(accent, 24));
        badge.setBorder(new EmptyBorder(6, 12, 6, 12));

        JPanel left = new JPanel(
                new FlowLayout(
                        FlowLayout.LEFT,
                        AppSpacing.MD,
                        0
                )
        );

        left.setOpaque(false);
        left.add(iconLabel);
        left.add(textCol);

        header.add(left, BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);

        return header;
    }

    // ------------------------------------------------------------------
    // Body: meta cards + description + snapshot (diff) section
    // ------------------------------------------------------------------

    private JPanel buildBody(
            ActivityLog log,
            String actionLabel,
            String entityLabel
    ) {

        JPanel body = new JPanel(
                new BorderLayout(0, AppSpacing.MD)
        );

        body.setOpaque(false);

        body.setBorder(
                new EmptyBorder(
                        AppSpacing.LG,
                        AppSpacing.XL,
                        AppSpacing.SM,
                        AppSpacing.XL
                )
        );

        body.add(
                buildMetaSection(log, entityLabel),
                BorderLayout.NORTH
        );

        body.add(
                buildSnapshotSection(log),
                BorderLayout.CENTER
        );

        return body;
    }

    private JPanel buildMetaSection(
            ActivityLog log,
            String entityLabel
    ) {

        JPanel section = new JPanel();

        section.setOpaque(false);

        section.setLayout(
                new BoxLayout(section, BoxLayout.Y_AXIS)
        );

        JPanel grid = new JPanel(
                new GridLayout(
                        2,
                        2,
                        AppSpacing.MD,
                        AppSpacing.MD
                )
        );

        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        grid.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 120)
        );

        String time = log.getCreatedAt() != null
                ? DATE_FORMAT.format(log.getCreatedAt())
                : "—";

        String user = log.getUsername() != null
                ? log.getUsername()
                : "SYSTEM";

        String target = entityLabel
                + (log.getRecordId() != null
                ? " #" + log.getRecordId()
                : "");

        grid.add(
                metaCard(
                        FontAwesomeSolid.CLOCK,
                        "Thời gian",
                        time,
                        AppColor.ACCENT
                )
        );

        grid.add(
                metaCard(
                        FontAwesomeSolid.USER,
                        "Người dùng",
                        user,
                        AppColor.SUCCESS
                )
        );

        grid.add(
                metaCard(
                        FontAwesomeSolid.TAG,
                        "Đối tượng",
                        target,
                        AppColor.WARNING
                )
        );

        grid.add(
                metaCard(
                        FontAwesomeSolid.HASHTAG,
                        "Log ID",
                        log.getLogId() > 0
                                ? String.valueOf(log.getLogId())
                                : "—",
                        AppColor.TEXT_MUTED
                )
        );

        section.add(grid);

        section.add(
                Box.createVerticalStrut(AppSpacing.MD)
        );

        String desc =
                log.getDescription() != null
                        && !log.getDescription().isBlank()
                        ? log.getDescription()
                        : "Không có mô tả.";

        section.add(descriptionCard(desc));

        return section;
    }

    private JPanel metaCard(
            FontAwesomeSolid icon,
            String label,
            String value,
            Color accent
    ) {

        JPanel card = new RoundedPanel(
                AppRadius.MEDIUM
        );

        card.setLayout(
                new BorderLayout(AppSpacing.SM, 0)
        );

        card.setBackground(AppColor.WHITE);

        card.setBorder(
                new EmptyBorder(
                        AppSpacing.MD,
                        AppSpacing.MD,
                        AppSpacing.MD,
                        AppSpacing.MD
                )
        );

        JLabel iconLbl = new JLabel(
                FontIcon.of(icon, 14, accent)
        );

        iconLbl.setVerticalAlignment(
                SwingConstants.TOP
        );

        JPanel text = new JPanel();

        text.setOpaque(false);

        text.setLayout(
                new BoxLayout(text, BoxLayout.Y_AXIS)
        );

        JLabel lbl = new JLabel(
                label.toUpperCase()
        );

        lbl.setFont(AppFont.SMALL_BOLD);
        lbl.setForeground(AppColor.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel(value);

        val.setFont(AppFont.BODY_BOLD);
        val.setForeground(AppColor.TEXT_TITLE);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);
        val.setToolTipText(value);

        text.add(lbl);
        text.add(Box.createVerticalStrut(4));
        text.add(val);

        card.add(iconLbl, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);

        return card;
    }

    private JPanel descriptionCard(String description) {

        JPanel card = new RoundedPanel(
                AppRadius.MEDIUM
        );

        card.setLayout(
                new BorderLayout(AppSpacing.SM, 0)
        );

        card.setBackground(AppColor.WHITE);

        card.setBorder(
                new EmptyBorder(
                        AppSpacing.MD,
                        AppSpacing.MD,
                        AppSpacing.MD,
                        AppSpacing.MD
                )
        );

        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 90)
        );

        JLabel iconLbl = new JLabel(
                FontIcon.of(
                        FontAwesomeSolid.ALIGN_LEFT,
                        14,
                        AppColor.ACCENT
                )
        );

        iconLbl.setVerticalAlignment(
                SwingConstants.TOP
        );

        JPanel text = new JPanel();

        text.setOpaque(false);

        text.setLayout(
                new BoxLayout(text, BoxLayout.Y_AXIS)
        );

        JLabel lbl = new JLabel("MÔ TẢ");

        lbl.setFont(AppFont.SMALL_BOLD);
        lbl.setForeground(AppColor.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea area = new JTextArea(description);

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(AppFont.BODY);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);

        text.add(lbl);
        text.add(Box.createVerticalStrut(4));
        text.add(area);

        card.add(iconLbl, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);

        return card;
    }

    // ------------------------------------------------------------------
    // Snapshot section
    // ------------------------------------------------------------------

    private JPanel buildSnapshotSection(ActivityLog log) {

        JPanel section = new JPanel(
                new BorderLayout(0, AppSpacing.SM)
        );

        section.setOpaque(false);

        boolean hasOld =
                log.getOldValue() != null
                        && !log.getOldValue().isBlank();

        boolean hasNew =
                log.getNewValue() != null
                        && !log.getNewValue().isBlank();

        // ---- Tiêu đề + nút chuyển chế độ xem ----

        JPanel titleRow = new JPanel(
                new BorderLayout()
        );

        titleRow.setOpaque(false);

        JLabel sectionTitle = new JLabel(
                "Những gì đã thay đổi"
        );

        sectionTitle.setFont(AppFont.BODY_BOLD);
        sectionTitle.setForeground(AppColor.TEXT_TITLE);

        toggleRawBtn = linkButton(
                "Xem JSON gốc"
        );

        toggleRawBtn.setVisible(
                hasOld || hasNew
        );

        // Icon FontIcon cho chế độ JSON gốc
        toggleRawBtn.setIcon(
                FontIcon.of(
                        FontAwesomeSolid.CODE,
                        12,
                        AppColor.ACCENT
                )
        );

        toggleRawBtn.setIconTextGap(6);

        toggleRawBtn.addActionListener(
                e -> toggleSnapshotView()
        );

        titleRow.add(
                sectionTitle,
                BorderLayout.WEST
        );

        titleRow.add(
                toggleRawBtn,
                BorderLayout.EAST
        );

        // ---- Card chứa nội dung ----

        JPanel card = new RoundedPanel(
                AppRadius.MEDIUM
        );

        card.setLayout(
                new BorderLayout()
        );

        card.setBackground(AppColor.WHITE);

        card.setBorder(
                new EmptyBorder(
                        AppSpacing.SM,
                        AppSpacing.SM,
                        AppSpacing.SM,
                        AppSpacing.SM
                )
        );

        snapshotCards = new CardLayout();

        snapshotCardPanel = new JPanel(
                snapshotCards
        );

        snapshotCardPanel.setOpaque(false);

        snapshotCardPanel.add(
                buildFriendlyDiffView(
                        log,
                        hasOld,
                        hasNew
                ),
                "friendly"
        );

        snapshotCardPanel.add(
                buildRawJsonView(
                        log,
                        hasOld,
                        hasNew
                ),
                "raw"
        );

        showingRaw = false;

        card.add(
                snapshotCardPanel,
                BorderLayout.CENTER
        );

        section.add(
                titleRow,
                BorderLayout.NORTH
        );

        section.add(
                card,
                BorderLayout.CENTER
        );

        return section;
    }

    private void toggleSnapshotView() {

        showingRaw = !showingRaw;

        snapshotCards.show(
                snapshotCardPanel,
                showingRaw ? "raw" : "friendly"
        );

        if (showingRaw) {

            toggleRawBtn.setText(
                    "Xem dạng dễ hiểu"
            );

            toggleRawBtn.setIcon(
                    FontIcon.of(
                            FontAwesomeSolid.ALIGN_JUSTIFY,
                            12,
                            AppColor.ACCENT
                    )
            );

        } else {

            toggleRawBtn.setText(
                    "Xem JSON gốc"
            );

            toggleRawBtn.setIcon(
                    FontIcon.of(
                            FontAwesomeSolid.CODE,
                            12,
                            AppColor.ACCENT
                    )
            );
        }
    }

    // ---- Chế độ 1: dạng dễ hiểu ----

    private JComponent buildFriendlyDiffView(
            ActivityLog log,
            boolean hasOld,
            boolean hasNew
    ) {

        if (!hasOld && !hasNew) {
            return buildEmptyState();
        }

        Map<String, String> oldMap =
                flatten(log.getOldValue());

        Map<String, String> newMap =
                flatten(log.getNewValue());

        if (!hasOld) {

            return buildFieldListView(
                    "Dữ liệu được tạo mới",
                    FontAwesomeSolid.PLUS_CIRCLE,
                    AppColor.SUCCESS,
                    newMap
            );
        }

        if (!hasNew) {

            return buildFieldListView(
                    "Dữ liệu trước khi xóa",
                    FontAwesomeSolid.TRASH,
                    AppColor.ERROR,
                    oldMap
            );
        }

        return buildDiffView(
                oldMap,
                newMap
        );
    }

    private JComponent buildFieldListView(
            String heading,
            FontAwesomeSolid icon,
            Color accent,
            Map<String, String> data
    ) {

        JPanel wrap = new JPanel();

        wrap.setOpaque(false);

        wrap.setLayout(
                new BoxLayout(
                        wrap,
                        BoxLayout.Y_AXIS
                )
        );

        wrap.setBorder(
                new EmptyBorder(
                        AppSpacing.SM,
                        AppSpacing.SM,
                        AppSpacing.SM,
                        AppSpacing.SM
                )
        );

        JLabel headingLbl = new JLabel(heading);

        headingLbl.setFont(AppFont.SMALL_BOLD);
        headingLbl.setForeground(accent);
        headingLbl.setIcon(
                FontIcon.of(icon, 12, accent)
        );
        headingLbl.setIconTextGap(6);
        headingLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        headingLbl.setBorder(
                new EmptyBorder(
                        4,
                        4,
                        AppSpacing.SM,
                        4
                )
        );

        wrap.add(headingLbl);

        if (data.isEmpty()) {

            wrap.add(
                    mutedNote(
                            "Không có dữ liệu chi tiết để hiển thị."
                    )
            );

        } else {

            JPanel grid = new JPanel(
                    new GridBagLayout()
            );

            grid.setOpaque(false);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);

            int row = 0;

            for (Map.Entry<String, String> e : data.entrySet()) {

                if (skipField(e.getKey())) {
                    continue;
                }

                addFieldRow(
                        grid,
                        row++,
                        label(e.getKey()),
                        displayValue(
                                e.getKey(),
                                e.getValue()
                        ),
                        AppColor.TEXT_PRIMARY,
                        false
                );
            }

            wrap.add(grid);
        }

        JScrollPane scroll = new JScrollPane(wrap);

        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(AppColor.WHITE);
        scroll.getViewport().setBackground(AppColor.WHITE);

        return scroll;
    }

    private JComponent buildDiffView(
            Map<String, String> oldMap,
            Map<String, String> newMap
    ) {

        Set<String> keys =
                new LinkedHashSet<>();

        keys.addAll(oldMap.keySet());
        keys.addAll(newMap.keySet());

        List<String> changedKeys =
                new ArrayList<>();

        List<String> unchangedKeys =
                new ArrayList<>();

        for (String key : keys) {

            if (skipField(key)) {
                continue;
            }

            String ov = oldMap.get(key);
            String nv = newMap.get(key);

            if (Objects.equals(
                    normalize(ov),
                    normalize(nv)
            )) {

                unchangedKeys.add(key);

            } else {

                changedKeys.add(key);
            }
        }

        JPanel wrap = new JPanel();

        wrap.setOpaque(false);

        wrap.setLayout(
                new BoxLayout(
                        wrap,
                        BoxLayout.Y_AXIS
                )
        );

        wrap.setBorder(
                new EmptyBorder(
                        AppSpacing.SM,
                        AppSpacing.SM,
                        AppSpacing.SM,
                        AppSpacing.SM
                )
        );

        if (changedKeys.isEmpty()) {

            wrap.add(
                    mutedNote(
                            "Dữ liệu trước và sau không có khác biệt nào được ghi nhận."
                    )
            );

        } else {

            JLabel changedHeading =
                    new JLabel(
                            "ĐÃ THAY ĐỔI (" +
                                    changedKeys.size() +
                                    ")"
                    );

            changedHeading.setFont(
                    AppFont.SMALL_BOLD
            );

            changedHeading.setForeground(
                    AppColor.WARNING
            );

            changedHeading.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );

            changedHeading.setBorder(
                    new EmptyBorder(
                            4,
                            4,
                            AppSpacing.SM,
                            4
                    )
            );

            wrap.add(changedHeading);

            for (String key : changedKeys) {

                wrap.add(
                        diffRow(
                                key,
                                oldMap.get(key),
                                newMap.get(key)
                        )
                );

                wrap.add(
                        Box.createVerticalStrut(
                                AppSpacing.SM
                        )
                );
            }
        }

        if (!unchangedKeys.isEmpty()) {

            wrap.add(
                    Box.createVerticalStrut(
                            AppSpacing.XS
                    )
            );

            wrap.add(
                    buildUnchangedToggle(
                            unchangedKeys,
                            oldMap
                    )
            );
        }

        JScrollPane scroll =
                new JScrollPane(wrap);

        scroll.setBorder(null);
        scroll.getVerticalScrollBar()
                .setUnitIncrement(16);

        scroll.setBackground(
                AppColor.WHITE
        );

        scroll.getViewport()
                .setBackground(
                        AppColor.WHITE
                );

        return scroll;
    }

    private JPanel diffRow(
            String key,
            String oldVal,
            String newVal
    ) {

        JPanel row =
                new RoundedPanel(
                        AppRadius.SMALL
                );

        row.setBackground(
                AppColor.WARNING_BG
        );

        row.setLayout(
                new BorderLayout()
        );

        row.setBorder(
                new EmptyBorder(
                        AppSpacing.SM,
                        AppSpacing.MD,
                        AppSpacing.SM,
                        AppSpacing.MD
                )
        );

        row.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        row.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                )
        );

        JLabel fieldLbl =
                new JLabel(label(key));

        fieldLbl.setFont(
                AppFont.SMALL_BOLD
        );

        fieldLbl.setForeground(
                AppColor.TEXT_TITLE
        );

        fieldLbl.setBorder(
                new EmptyBorder(
                        0,
                        0,
                        6,
                        0
                )
        );

        JPanel values = new JPanel();

        values.setOpaque(false);

        values.setLayout(
                new BoxLayout(
                        values,
                        BoxLayout.Y_AXIS
                )
        );

        values.add(
                valueLine(
                        FontAwesomeSolid.MINUS_CIRCLE,
                        AppColor.ERROR,
                        displayValue(
                                key,
                                oldVal
                        )
                )
        );

        values.add(
                Box.createVerticalStrut(3)
        );

        values.add(
                valueLine(
                        FontAwesomeSolid.PLUS_CIRCLE,
                        AppColor.SUCCESS,
                        displayValue(
                                key,
                                newVal
                        )
                )
        );

        JPanel body =
                new JPanel(
                        new BorderLayout()
                );

        body.setOpaque(false);

        body.add(
                fieldLbl,
                BorderLayout.NORTH
        );

        body.add(
                values,
                BorderLayout.CENTER
        );

        row.add(
                body,
                BorderLayout.CENTER
        );

        return row;
    }

    private JPanel valueLine(
            FontAwesomeSolid icon,
            Color color,
            String text
    ) {

        JPanel line =
                new JPanel(
                        new BorderLayout(6, 0)
                );

        line.setOpaque(false);

        JLabel iconLbl =
                new JLabel(
                        FontIcon.of(
                                icon,
                                11,
                                color
                        )
                );

        iconLbl.setVerticalAlignment(
                SwingConstants.TOP
        );

        iconLbl.setBorder(
                new EmptyBorder(
                        2,
                        0,
                        0,
                        0
                )
        );

        JTextArea valueArea =
                new JTextArea(text);

        valueArea.setEditable(false);
        valueArea.setLineWrap(true);
        valueArea.setWrapStyleWord(true);
        valueArea.setOpaque(false);
        valueArea.setFont(AppFont.BODY);
        valueArea.setForeground(color);
        valueArea.setBorder(null);

        line.add(
                iconLbl,
                BorderLayout.WEST
        );

        line.add(
                valueArea,
                BorderLayout.CENTER
        );

        return line;
    }

    private JPanel buildUnchangedToggle(
            List<String> unchangedKeys,
            Map<String, String> oldMap
    ) {

        JPanel holder = new JPanel();

        holder.setOpaque(false);

        holder.setLayout(
                new BoxLayout(
                        holder,
                        BoxLayout.Y_AXIS
                )
        );

        holder.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        JButton toggle =
                linkButton(
                        "Hiện " +
                                unchangedKeys.size() +
                                " trường không thay đổi"
                );

        toggle.setIcon(
                FontIcon.of(
                        FontAwesomeSolid.CHEVRON_DOWN,
                        11,
                        AppColor.ACCENT
                )
        );

        toggle.setIconTextGap(6);

        toggle.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        JPanel detail =
                new JPanel(
                        new GridBagLayout()
                );

        detail.setOpaque(false);

        detail.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        detail.setVisible(false);

        detail.setBorder(
                new EmptyBorder(
                        AppSpacing.SM,
                        4,
                        0,
                        4
                )
        );

        int row = 0;

        for (String key : unchangedKeys) {

            addFieldRow(
                    detail,
                    row++,
                    label(key),
                    displayValue(
                            key,
                            oldMap.get(key)
                    ),
                    AppColor.TEXT_MUTED,
                    true
            );
        }

        toggle.addActionListener(e -> {

            boolean nowVisible =
                    !detail.isVisible();

            detail.setVisible(
                    nowVisible
            );

            toggle.setText(
                    (nowVisible
                            ? "Ẩn "
                            : "Hiện ") +
                            unchangedKeys.size() +
                            " trường không thay đổi"
            );

            toggle.setIcon(
                    FontIcon.of(
                            nowVisible
                                    ? FontAwesomeSolid.CHEVRON_UP
                                    : FontAwesomeSolid.CHEVRON_DOWN,
                            11,
                            AppColor.ACCENT
                    )
            );

            holder.revalidate();
            holder.repaint();
        });

        holder.add(toggle);
        holder.add(detail);

        return holder;
    }

    private void addFieldRow(
            JPanel grid,
            int row,
            String fieldLabel,
            String value,
            Color valueColor,
            boolean muted
    ) {

        GridBagConstraints gcLabel =
                new GridBagConstraints();

        gcLabel.gridx = 0;
        gcLabel.gridy = row;
        gcLabel.anchor =
                GridBagConstraints.NORTHWEST;

        gcLabel.insets =
                new Insets(
                        4,
                        4,
                        4,
                        AppSpacing.MD
                );

        JLabel lbl =
                new JLabel(fieldLabel);

        lbl.setFont(
                muted
                        ? AppFont.SMALL
                        : AppFont.SMALL_BOLD
        );

        lbl.setForeground(
                AppColor.TEXT_MUTED
        );

        grid.add(
                lbl,
                gcLabel
        );

        GridBagConstraints gcValue =
                new GridBagConstraints();

        gcValue.gridx = 1;
        gcValue.gridy = row;
        gcValue.weightx = 1.0;
        gcValue.fill =
                GridBagConstraints.HORIZONTAL;

        gcValue.anchor =
                GridBagConstraints.NORTHWEST;

        gcValue.insets =
                new Insets(
                        4,
                        0,
                        4,
                        4
                );

        JTextArea val =
                new JTextArea(value);

        val.setEditable(false);
        val.setLineWrap(true);
        val.setWrapStyleWord(true);
        val.setOpaque(false);
        val.setFont(AppFont.BODY);
        val.setForeground(valueColor);
        val.setBorder(null);

        grid.add(
                val,
                gcValue
        );
    }

    private JLabel mutedNote(String text) {

        JLabel lbl =
                new JLabel(text);

        lbl.setFont(AppFont.BODY);
        lbl.setForeground(
                AppColor.TEXT_MUTED
        );

        lbl.setBorder(
                new EmptyBorder(
                        AppSpacing.MD,
                        AppSpacing.SM,
                        AppSpacing.MD,
                        AppSpacing.SM
                )
        );

        return lbl;
    }

    private JButton linkButton(String text) {

        JButton btn =
                new JButton(text);

        btn.setFont(
                AppFont.SMALL_BOLD
        );

        btn.setForeground(
                AppColor.ACCENT
        );

        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);

        btn.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        btn.setBorder(
                new EmptyBorder(
                        4,
                        4,
                        4,
                        4
                )
        );

        btn.addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mouseEntered(
                            MouseEvent e
                    ) {

                        btn.setForeground(
                                AppColor.ACCENT_HOVER
                        );
                    }

                    @Override
                    public void mouseExited(
                            MouseEvent e
                    ) {

                        btn.setForeground(
                                AppColor.ACCENT
                        );
                    }
                }
        );

        return btn;
    }

    private JPanel buildEmptyState() {

        JPanel emptyPane =
                new JPanel(
                        new GridBagLayout()
                );

        emptyPane.setBackground(
                AppColor.WHITE
        );

        emptyPane.setBorder(
                new EmptyBorder(
                        AppSpacing.XL,
                        AppSpacing.XL,
                        AppSpacing.XL,
                        AppSpacing.XL
                )
        );

        JPanel inner = new JPanel();

        inner.setOpaque(false);

        inner.setLayout(
                new BoxLayout(
                        inner,
                        BoxLayout.Y_AXIS
                )
        );

        JLabel icon =
                new JLabel(
                        FontIcon.of(
                                FontAwesomeSolid.FILE_CODE,
                                28,
                                AppColor.TEXT_MUTED
                        )
                );

        icon.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel msg =
                new JLabel(
                        "Không có dữ liệu chi tiết cho hành động này"
                );

        msg.setFont(AppFont.BODY);
        msg.setForeground(
                AppColor.TEXT_MUTED
        );

        msg.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JLabel hint =
                new JLabel(
                        "Một số hành động (đăng nhập, khóa tài khoản...) không lưu trạng thái trước/sau."
                );

        hint.setFont(AppFont.SMALL);
        hint.setForeground(
                AppColor.TEXT_MUTED
        );

        hint.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        inner.add(icon);

        inner.add(
                Box.createVerticalStrut(
                        AppSpacing.MD
                )
        );

        inner.add(msg);

        inner.add(
                Box.createVerticalStrut(4)
        );

        inner.add(hint);

        emptyPane.add(inner);

        return emptyPane;
    }

    // ------------------------------------------------------------------
    // Chế độ 2: JSON gốc
    // ------------------------------------------------------------------

    private JComponent buildRawJsonView(
            ActivityLog log,
            boolean hasOld,
            boolean hasNew
    ) {

        JTabbedPane tabs =
                new JTabbedPane();

        tabs.setFont(AppFont.BODY);
        tabs.setBackground(
                AppColor.WHITE
        );

        tabs.setForeground(
                AppColor.TEXT_PRIMARY
        );

        tabs.addTab(
                "  Trước thay đổi  ",
                buildJsonPane(
                        log.getOldValue(),
                        !hasOld
                )
        );

        tabs.addTab(
                "  Sau thay đổi  ",
                buildJsonPane(
                        log.getNewValue(),
                        !hasNew
                )
        );

        if (!hasOld && hasNew) {
            tabs.setSelectedIndex(1);
        }

        return tabs;
    }

    private JComponent buildJsonPane(
            String json,
            boolean empty
    ) {

        if (empty) {
            return buildEmptyState();
        }

        String display;

        try {

            Object parsed =
                    JsonParser.parseString(json);

            display =
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .disableHtmlEscaping()
                            .create()
                            .toJson(parsed);

        } catch (Exception e) {

            display = json;
        }

        JTextArea area =
                new JTextArea(display);

        area.setEditable(false);

        area.setFont(
                new Font(
                        "Consolas",
                        Font.PLAIN,
                        13
                )
        );

        area.setBackground(
                AppColor.PAGE_BG
        );

        area.setForeground(
                AppColor.TEXT_PRIMARY
        );

        area.setCaretPosition(0);

        area.setBorder(
                new EmptyBorder(
                        AppSpacing.MD,
                        AppSpacing.MD,
                        AppSpacing.MD,
                        AppSpacing.MD
                )
        );

        area.setLineWrap(false);

        JScrollPane scroll =
                new JScrollPane(area);

        scroll.setBorder(null);

        scroll.getVerticalScrollBar()
                .setUnitIncrement(16);

        scroll.setBackground(
                AppColor.PAGE_BG
        );

        scroll.getViewport()
                .setBackground(
                        AppColor.PAGE_BG
                );

        return scroll;
    }

    // ------------------------------------------------------------------
    // Footer
    // ------------------------------------------------------------------

    private JPanel buildFooter() {

        JPanel footer =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                0,
                                0
                        )
                );

        footer.setOpaque(true);

        footer.setBackground(
                AppColor.WHITE
        );

        footer.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(
                                1,
                                0,
                                0,
                                0,
                                AppColor.BORDER
                        ),
                        new EmptyBorder(
                                AppSpacing.MD,
                                AppSpacing.XL,
                                AppSpacing.MD,
                                AppSpacing.XL
                        )
                )
        );

        JButton close =
                new JButton("Đóng");

        close.setFont(
                AppFont.BUTTON
        );

        close.setForeground(
                Color.WHITE
        );

        close.setBackground(
                AppColor.ACCENT
        );

        close.setFocusPainted(false);
        close.setBorderPainted(false);

        close.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        close.setPreferredSize(
                new Dimension(110, 38)
        );

        close.addActionListener(
                e -> dispose()
        );

        close.addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mouseEntered(
                            MouseEvent e
                    ) {

                        close.setBackground(
                                AppColor.ACCENT_HOVER
                        );
                    }

                    @Override
                    public void mouseExited(
                            MouseEvent e
                    ) {

                        close.setBackground(
                                AppColor.ACCENT
                        );
                    }
                }
        );

        footer.add(close);

        getRootPane()
                .setDefaultButton(close);

        return footer;
    }

    // ------------------------------------------------------------------
    // Helpers — parse / flatten / format / humanize JSON snapshot
    // ------------------------------------------------------------------

    private static boolean skipField(String key) {

        return key == null || key.isBlank();
    }

    private static Map<String, String> flatten(
            String json
    ) {

        Map<String, String> out =
                new LinkedHashMap<>();

        if (json == null || json.isBlank()) {
            return out;
        }

        try {

            JsonElement root =
                    JsonParser.parseString(json);

            if (root.isJsonObject()) {

                flattenObject(
                        "",
                        root.getAsJsonObject(),
                        out
                );

            } else {

                out.put(
                        "value",
                        root.toString()
                );
            }

        } catch (Exception e) {

            out.put("raw", json);
        }

        return out;
    }

    private static void flattenObject(
            String prefix,
            JsonObject obj,
            Map<String, String> out
    ) {

        for (
                Map.Entry<String, JsonElement> e
                : obj.entrySet()
        ) {

            String key =
                    prefix.isEmpty()
                            ? e.getKey()
                            : prefix + "." + e.getKey();

            JsonElement val =
                    e.getValue();

            if (val == null || val.isJsonNull()) {

                out.put(key, null);

            } else if (val.isJsonObject()) {

                flattenObject(
                        key,
                        val.getAsJsonObject(),
                        out
                );

            } else if (val.isJsonArray()) {

                out.put(
                        key,
                        val.toString()
                );

            } else {

                out.put(
                        key,
                        val.getAsString()
                );
            }
        }
    }

    private static String normalize(String v) {

        return v == null
                ? ""
                : v.trim();
    }

    private static String displayValue(
            String key,
            String raw
    ) {

        if (raw == null || raw.isBlank()) {
            return "(trống)";
        }

        String v = raw.trim();

        if (v.equalsIgnoreCase("true")) {
            return "Có";
        }

        if (v.equalsIgnoreCase("false")) {
            return "Không";
        }

        String keyLower =
                key == null
                        ? ""
                        : key.toLowerCase(
                                Locale.ROOT
                        );

        String asDate =
                tryFormatDate(v);

        if (asDate != null) {
            return asDate;
        }

        if (
                v.matches(
                        "-?\\d+(\\.\\d+)?"
                )
                        && looksNumeric(keyLower)
        ) {

            try {

                double d =
                        Double.parseDouble(v);

                return NUMBER_FORMAT.format(d);

            } catch (
                    NumberFormatException ignored
            ) {
                // giữ nguyên chuỗi gốc
            }
        }

        return v;
    }

    private static boolean looksNumeric(
            String keyLower
    ) {

        for (String hint : NUMERIC_HINT_KEYS) {

            if (keyLower.contains(hint)) {
                return true;
            }
        }

        return false;
    }

    private static String tryFormatDate(
            String v
    ) {

        try {

            if (v.contains("T")) {

                if (
                        v.matches(
                                ".*[+-]\\d{2}:?\\d{2}$"
                        )
                                || v.endsWith("Z")
                ) {

                    OffsetDateTime odt =
                            OffsetDateTime.parse(v);

                    return odt.format(
                            DateTimeFormatter.ofPattern(
                                    "dd/MM/yyyy HH:mm:ss"
                            )
                    );
                }

                LocalDateTime ldt =
                        LocalDateTime.parse(
                                v.length() > 19
                                        ? v.substring(0, 19)
                                        : v
                        );

                return ldt.format(
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy HH:mm:ss"
                        )
                );
            }

        } catch (Exception ignored) {
            // không đúng định dạng ngày giờ
        }

        return null;
    }

    private static String label(String key) {

        if (key == null) {
            return "";
        }

        String leaf =
                key.contains(".")
                        ? key.substring(
                                key.lastIndexOf('.') + 1
                        )
                        : key;

        String lookup =
                leaf.toLowerCase(
                        Locale.ROOT
                );

        String known =
                FIELD_LABELS.get(lookup);

        if (known != null) {
            return known;
        }

        return humanize(leaf);
    }

    private static String humanize(
            String raw
    ) {

        String spaced =
                raw
                        .replaceAll(
                                "([a-z0-9])([A-Z])",
                                "$1 $2"
                        )
                        .replace('_', ' ')
                        .trim();

        if (spaced.isEmpty()) {
            return raw;
        }

        String[] words =
                spaced.split("\\s+");

        StringBuilder sb =
                new StringBuilder();

        for (String w : words) {

            if (w.isEmpty()) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append(' ');
            }

            sb.append(
                    Character.toUpperCase(
                            w.charAt(0)
                    )
            );

            sb.append(
                    w.substring(1)
                            .toLowerCase(
                                    Locale.ROOT
                            )
            );
        }

        return sb.toString();
    }

    private static Map<String, String>
    buildFieldLabels() {

        Map<String, String> m =
                new LinkedHashMap<>();

        // Chung

        m.put("id", "ID");
        m.put("status", "Trạng thái");
        m.put("note", "Ghi chú");
        m.put("reason", "Lý do");
        m.put("type", "Loại");
        m.put("code", "Mã");
        m.put("createdat", "Ngày tạo");
        m.put("updatedat", "Ngày cập nhật");
        m.put("createdby", "Người tạo");
        m.put("createdbyname", "Người tạo");
        m.put("description", "Mô tả");

        // Sản phẩm / danh mục

        m.put("productid", "Mã sản phẩm");
        m.put("productcode", "Mã SP");
        m.put("productname", "Tên sản phẩm");
        m.put("categoryid", "Danh mục");
        m.put("categoryname", "Danh mục");
        m.put("brand", "Thương hiệu");
        m.put("imageurl", "Ảnh sản phẩm");
        m.put("importprice", "Giá nhập");
        m.put("sellprice", "Giá bán");
        m.put("margin", "Biên lợi nhuận");
        m.put("minstock", "Tồn kho tối thiểu");
        m.put("stock", "Tồn kho");
        m.put("unit", "Đơn vị tính");
        m.put("weightvolume", "Khối lượng / Thể tích");
        m.put("activeproductcount", "Số SP đang bán");

        // Người dùng / nhân viên / khách hàng

        m.put("userid", "Mã người dùng");
        m.put("username", "Tên đăng nhập");
        m.put("fullname", "Họ và tên");
        m.put("email", "Email");
        m.put("phone", "Số điện thoại");
        m.put("avatarurl", "Ảnh đại diện");
        m.put("role", "Vai trò");
        m.put("locked", "Khóa tài khoản");
        m.put("failedlogincount", "Số lần đăng nhập sai");
        m.put("employeeid", "Mã nhân viên");
        m.put("employeecode", "Mã nhân viên");
        m.put("dateofbirth", "Ngày sinh");
        m.put("gender", "Giới tính");
        m.put("hiredate", "Ngày vào làm");
        m.put("salary", "Lương");
        m.put("customerid", "Mã khách hàng");
        m.put("customercode", "Mã khách hàng");
        m.put("memberpoint", "Điểm thành viên");

        // Nhà cung cấp

        m.put("supplierid", "Mã nhà cung cấp");
        m.put("suppliername", "Tên nhà cung cấp");
        m.put("address", "Địa chỉ");
        m.put("debtbalance", "Công nợ");
        m.put("productcount", "Số sản phẩm cung cấp");
        m.put("supplieditems", "Mặt hàng cung cấp");

        // Hóa đơn / đơn hàng

        m.put("invoiceid", "Mã hóa đơn");
        m.put("invoicecode", "Mã hóa đơn");
        m.put("orderid", "Mã đơn hàng");
        m.put("ordercode", "Mã đơn hàng");
        m.put("orderstatus", "Trạng thái đơn hàng");
        m.put("customername", "Khách hàng");
        m.put("customerphone", "SĐT khách hàng");
        m.put("customeremail", "Email khách hàng");
        m.put("itemcount", "Số mặt hàng");
        m.put("paymentmethod", "Phương thức thanh toán");
        m.put("paymentstatus", "Trạng thái thanh toán");
        m.put("pointsearned", "Điểm tích được");
        m.put("pointsused", "Điểm sử dụng");
        m.put("promotioncode", "Mã khuyến mãi");
        m.put("promotionid", "Chương trình khuyến mãi");
        m.put("shiftid", "Ca làm việc");
        m.put("subtotal", "Tạm tính");
        m.put("totalamount", "Tổng tiền");
        m.put("vatamount", "Tiền thuế VAT");
        m.put("vatrate", "Thuế suất VAT");
        m.put("cancelreason", "Lý do hủy");
        m.put("cancelledat", "Thời điểm hủy");
        m.put("completedat", "Thời điểm hoàn tất");
        m.put("approvedreturncount", "Số lượt trả hàng đã duyệt");
        m.put("paypalcaptureid", "Mã giao dịch PayPal");
        m.put("paypalorderid", "Mã đơn PayPal");
        m.put("shippingaddress", "Địa chỉ giao hàng");
        m.put("returnrequested", "Có yêu cầu trả hàng");
        m.put("seenbyadmin", "Admin đã xem");

        // Phiếu nhập / lô hàng / cảnh báo tồn

        m.put("receiptid", "Mã phiếu nhập");
        m.put("receiptcode", "Mã phiếu nhập");
        m.put("batchid", "Mã lô hàng");
        m.put("batchcode", "Mã lô hàng");
        m.put("expirydate", "Hạn sử dụng");
        m.put("importdate", "Ngày nhập");
        m.put("lotnumber", "Số lô");
        m.put("manufacturedate", "Ngày sản xuất");
        m.put("quantity", "Số lượng");
        m.put("remainingqty", "Số lượng còn lại");
        m.put("alertid", "Mã cảnh báo");
        m.put("alerttype", "Loại cảnh báo");
        m.put("reportedby", "Người báo cáo");
        m.put("reportedbyname", "Người báo cáo");
        m.put("resolvedat", "Thời điểm xử lý");
        m.put("resolvedby", "Người xử lý");
        m.put("resolvedbyname", "Người xử lý");
        m.put("seenbyinventorymanager", "QL kho đã xem");
        m.put("stockatreport", "Tồn kho tại thời điểm báo cáo");

        // Ca làm việc / thu chi

        m.put("cashdifference", "Chênh lệch tiền mặt");
        m.put("closedby", "Người đóng ca");
        m.put("closedbyname", "Người đóng ca");
        m.put("closingnote", "Ghi chú đóng ca");
        m.put("countedcash", "Tiền mặt kiểm đếm");
        m.put("endtime", "Giờ kết thúc");
        m.put("starttime", "Giờ bắt đầu");
        m.put("expectedcash", "Tiền mặt dự kiến");
        m.put("invoicecount", "Số hóa đơn trong ca");
        m.put("openingnote", "Ghi chú mở ca");
        m.put("username", "Nhân viên");
        m.put("cashtransactionid", "Mã giao dịch quỹ");
        m.put("amount", "Số tiền");
        m.put("transactioncode", "Mã giao dịch");
        m.put("transactiontype", "Loại giao dịch");

        // Trả hàng / đổi hàng

        m.put("returnid", "Mã phiếu trả");
        m.put("approvedat", "Thời điểm duyệt");
        m.put("approvedby", "Người duyệt");
        m.put("approvedbyname", "Người duyệt");
        m.put("discountshare", "Phần giảm giá phân bổ");
        m.put("pointsshare", "Phần điểm phân bổ");
        m.put("requiresapproval", "Yêu cầu phê duyệt");
        m.put("rejectionreason", "Lý do từ chối");
        m.put("totalvalue", "Giá trị");
        m.put("supplierreturnid", "Mã phiếu trả NCC");
        m.put("supplierreturncode", "Mã phiếu trả NCC");
        m.put("totalrefundamount", "Số tiền hoàn trả");

        return m;
    }

    // ------------------------------------------------------------------
    // Action -> màu / icon
    // ------------------------------------------------------------------

    private static Color actionColor(
            String action
    ) {

        if (action == null) {
            return AppColor.ACCENT;
        }

        switch (action) {

            case ActivityLog.ACTION_CREATE:
            case ActivityLog.ACTION_RESTORE:
                return AppColor.SUCCESS;

            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE:
            case ActivityLog.ACTION_LOGIN_FAILED:
            case "USER_LOCK":
                return AppColor.ERROR;

            case ActivityLog.ACTION_UPDATE:
            case ActivityLog.ACTION_STATUS_CHANGE:
            case ActivityLog.ACTION_PASSWORD_RESET:
            case "USER_UNLOCK":
                return AppColor.WARNING;

            case ActivityLog.ACTION_LOGIN:
            case ActivityLog.ACTION_LOGOUT:
                return AppColor.ACCENT;

            default:
                return AppColor.ACCENT;
        }
    }

    private static FontAwesomeSolid actionIcon(
            String action
    ) {

        if (action == null) {
            return FontAwesomeSolid.HISTORY;
        }

        switch (action) {

            case ActivityLog.ACTION_CREATE:
                return FontAwesomeSolid.PLUS_CIRCLE;

            case ActivityLog.ACTION_UPDATE:
                return FontAwesomeSolid.EDIT;

            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE:
                return FontAwesomeSolid.TRASH;

            case ActivityLog.ACTION_RESTORE:
                return FontAwesomeSolid.UNDO;

            case ActivityLog.ACTION_LOGIN:
                return FontAwesomeSolid.SIGN_IN_ALT;

            case ActivityLog.ACTION_LOGOUT:
                return FontAwesomeSolid.SIGN_OUT_ALT;

            case ActivityLog.ACTION_LOGIN_FAILED:
                return FontAwesomeSolid.EXCLAMATION_TRIANGLE;

            case ActivityLog.ACTION_PASSWORD_RESET:
                return FontAwesomeSolid.KEY;

            case ActivityLog.ACTION_STATUS_CHANGE:
                return FontAwesomeSolid.TOGGLE_ON;

            case "USER_LOCK":
                return FontAwesomeSolid.LOCK;

            case "USER_UNLOCK":
                return FontAwesomeSolid.UNLOCK;

            default:
                return FontAwesomeSolid.HISTORY;
        }
    }

    private static Color soft(
            Color base,
            int alpha
    ) {

        return new Color(
                base.getRed(),
                base.getGreen(),
                base.getBlue(),
                alpha
        );
    }

    /**
     * Panel bo góc nhẹ, vẽ nền + viền.
     */
    private static class RoundedPanel
            extends JPanel {

        private final int radius;

        RoundedPanel(int radius) {

            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(
                Graphics g
        ) {

            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int w = getWidth();
            int h = getHeight();

            RoundRectangle2D.Float shape =
                    new RoundRectangle2D.Float(
                            0,
                            0,
                            w - 1,
                            h - 1,
                            radius,
                            radius
                    );

            g2.setColor(
                    getBackground()
            );

            g2.fill(shape);

            g2.setColor(
                    AppColor.BORDER
            );

            g2.draw(shape);

            g2.dispose();

            super.paintComponent(g);
        }
    }

    static void show(
            Window owner,
            ActivityLog log,
            String actionLabel,
            String entityLabel
    ) {

        new AuditLogDetailDialog(
                owner,
                log,
                actionLabel,
                entityLabel
        ).setVisible(true);
    }
}