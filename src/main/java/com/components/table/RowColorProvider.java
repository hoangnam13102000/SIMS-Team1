package com.components.table;

import java.awt.Color;

/**
 * Cau noi mau sac dung chung cho moi renderer trong package nay (ActionColumn,
 * StatusColumn, ImageColumn, AutoRowNumber...). Moi table "chu" (vd BaseTable)
 * chi can implement 1 lambda tra ve mau nen theo (viewRow, isSelected) - thuong
 * la logic striped-row (chan/le) + mau khi dang chon - roi truyen vao cac
 * renderer o day. Nho vay cac column trong package table khong phu thuoc nguoc
 * vao BaseTable, co the tai su dung cho bat ky JTable/topic nao khac.
 */
@FunctionalInterface
public interface RowColorProvider {
    Color colorFor(int viewRow, boolean isSelected);
}