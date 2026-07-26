package com.permission;

/**
 * Danh dau 1 quyen (permission) trong he thong. Moi domain (shop, HR, ngan
 * hang...) tu dinh nghia enum rieng implement interface nay - KHONG can sua
 * bat ky class nao trong package com.core.permission khi doi sang domain
 * khac, chi can them 1 enum moi o tang model cua domain do.
 *
 * Vi du:
 *   public enum AppPermission implements Permission { PHONE_VIEW, PHONE_EDIT }
 */
public interface Permission {

    /** Khoa duy nhat cua quyen. Mac dinh dung ten enum constant (toString()). */
    default String key() {
        return toString();
    }
}