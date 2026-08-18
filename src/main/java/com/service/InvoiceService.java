package com.service;

import com.dao.InvoiceDAO;
import com.model.Invoice;
import com.model.User;
import com.model.permission.AppPermission;
import com.utils.PaginationHelper;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class InvoiceService {

    private final InvoiceDAO invoiceDAO;
    private final AuthService authService;

    public InvoiceService() {
        this(
                new InvoiceDAO(),
                AuthService.getInstance()
        );
    }

    public InvoiceService(
            InvoiceDAO invoiceDAO,
            AuthService authService
    ) {
        this.invoiceDAO = invoiceDAO;
        this.authService = authService;
    }

    public PaginationHelper.PaginationResult<Invoice>
    getVisiblePaged(
            int page,
            int pageSize,
            String keyword,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        User user =
                authService.getCurrentUser();

        if (
            user == null
            || !canViewInvoices()
        ) {
            return emptyPage(
                    page,
                    pageSize
            );
        }

        Integer ownerFilter =
                resolveOwnerFilter(user);

        return invoiceDAO.getPagedFiltered(
                page,
                pageSize,
                keyword,
                fromDate,
                toDate,
                ownerFilter
        );
    }

    public List<Invoice> getVisibleAll() {

        User user =
                authService.getCurrentUser();

        if (
            user == null
            || !canViewInvoices()
        ) {
            return Collections.emptyList();
        }

        return invoiceDAO.getAllFiltered(
                resolveOwnerFilter(user)
        );
    }

    public Invoice findVisibleById(
            int invoiceId
    ) {

        User user =
                authService.getCurrentUser();

        if (
            user == null
            || !canViewInvoices()
        ) {
            return null;
        }

        return invoiceDAO.findByIdVisible(
                invoiceId,
                resolveOwnerFilter(user)
        );
    }

    private boolean canViewInvoices() {

        return authService.can(
                AppPermission.INVOICE_VIEW_OWN
        ) || authService.can(
                AppPermission.INVOICE_VIEW_ALL
        );
    }

    private Integer resolveOwnerFilter(
            User user
    ) {

        if (
            authService.can(
                    AppPermission.INVOICE_VIEW_ALL
            )
        ) {
            return null;
        }

        return user.getUserId();
    }

    private PaginationHelper.PaginationResult<Invoice>
    emptyPage(
            int page,
            int pageSize
    ) {

        PaginationHelper.PaginationResult<Invoice>
                result =
                new PaginationHelper.PaginationResult<>();

        result.setData(
                Collections.emptyList()
        );

        result.setCurrentPage(page);
        result.setPageSize(pageSize);
        result.setTotalRecords(0);
        result.setTotalPages(0);

        return result;
    }
}