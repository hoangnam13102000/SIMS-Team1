package com.components.table;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.util.Comparator;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
/**
 * Wrapper quanh javax.swing.table.TableRowSorter voi API fluent tien dung hon
 * cho BaseTable: tat sort cho nhung cot khong nen sort (cot Anh, cot Thao tac,
 * cot STT), va comparator san cho cot dang tien/so co dinh dang chuoi (vd
 * "12.000.000" hay "1.234 VND").
 *
 * Luu y: vi du lieu trong BaseTable thuong duoc phan trang o server (xem
 * PaginationHelper), sort o day chi sap xep trong pham vi cac dong dang hien
 * thi tren trang hien tai - khong sort toan bo tap du lieu. Phu hop cho bang
 * du lieu nho / da tai het (vd danh muc, log) hoac de sap xep nhanh trong 1 trang.
 */
public class TableSorter extends TableRowSorter<DefaultTableModel> {

    public TableSorter(DefaultTableModel model) {
        super(model);
    }

    /** Tat kha nang sort cho cac cot chi mang tinh hien thi (action, anh, STT...). */
    public TableSorter disableSortingFor(int... columnIndexes) {
        for (int c : columnIndexes) {
            if (c >= 0) setSortable(c, false);
        }
        return this;
    }

    public TableSorter comparator(int columnIndex, Comparator<Object> comparator) {
        setComparator(columnIndex, comparator);
        return this;
    }

    /**
     * Comparator cho cot dang chuoi co dinh dang so (vd "12.000.000", "1.234 VND",
     * "-50"): bo tat ca ky tu khong phai chu so/dau tru roi so sanh nhu so nguyen.
     */
    public static Comparator<Object> numericStringComparator() {
        return Comparator.comparingLong(TableSorter::extractNumber);
    }

    private static long extractNumber(Object o) {
        if (o == null) return 0L;
        String digits = o.toString().replaceAll("[^0-9-]", "");
        if (digits.isEmpty() || digits.equals("-")) return 0L;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}