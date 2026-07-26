package com.components.table;

import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TableFilter {

    private final TableRowSorter<DefaultTableModel> sorter;
    private int[] filterColumns; // null = tat ca cot
    private String currentQuery = "";
    private RowFilter<Object, Object> extraFilter;

    public TableFilter(TableRowSorter<DefaultTableModel> sorter) {
        this.sorter = sorter;
    }

    /** Gioi han loc theo tu khoa chi tren mot so cot (mac dinh: tat ca cot). */
    public TableFilter columns(int... columnIndexes) {
        this.filterColumns = columnIndexes;
        return this;
    }

    /**
     * Them 1 dieu kien loc bo sung (vd loc theo trang thai dang chon o combobox),
     * chay song song (AND) voi tu khoa tim kiem. Truyen null de bo dieu kien nay.
     */
    public TableFilter setExtraFilter(Predicate<RowFilter.Entry<?, ?>> predicate) {
        this.extraFilter = predicate == null ? null : new RowFilter<Object, Object>() {
            @Override
            public boolean include(Entry<?, ?> entry) {
                return predicate.test(entry);
            }
        };
        reapply();
        return this;
    }

    /** Loc theo tu khoa. Truyen chuoi rong/null de bo loc tu khoa (van giu extra filter neu co). */
    public void filter(String query) {
        this.currentQuery = query == null ? "" : query.trim();
        reapply();
    }

    /** Bo toan bo dieu kien loc (ca tu khoa lan extra filter). */
    public void clear() {
        currentQuery = "";
        extraFilter = null;
        sorter.setRowFilter(null);
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    /** Ap dung lai bo loc hien tai (goi lai sau khi doi extraFilter tu ben ngoai). */
    public void reapply() {
        List<RowFilter<Object, Object>> active = new ArrayList<>();

        if (!currentQuery.isEmpty()) {
            RowFilter<Object, Object> textFilter = buildTextFilter(currentQuery, filterColumns);
            if (textFilter != null) active.add(textFilter);
        }
        if (extraFilter != null) {
            active.add(extraFilter);
        }

        if (active.isEmpty()) {
            sorter.setRowFilter(null);
        } else if (active.size() == 1) {
            sorter.setRowFilter(active.get(0));
        } else {
            sorter.setRowFilter(RowFilter.andFilter(active));
        }
    }

    private static RowFilter<Object, Object> buildTextFilter(String query, int[] columns) {
        try {
            String pattern = "(?i)" + Pattern.quote(query);
            return columns == null ? RowFilter.regexFilter(pattern) : RowFilter.regexFilter(pattern, columns);
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }
}