SIMS MySQL - Goi import
========================

Thu tu chay (phpMyAdmin Import hoac mysql CLI):

  1. 01_SIMS_Schema_MySQL.sql     -> tao DB + bang
  2. 03_SIMS_SampleData_MySQL.sql -> du lieu mau day du (tuy chon)
  3. 02_SIMS_Triggers_MySQL.sql   -> ham/trigger nghiep vu + gan ma con thieu

Neu KHONG can du lieu mau:
  1. 01_SIMS_Schema_MySQL.sql
  2. 00_RBAC_Admin_MySQL.sql
  3. 02_SIMS_Triggers_MySQL.sql

CLI:
  mysql -u root -p < 01_SIMS_Schema_MySQL.sql
  mysql -u root -p < 03_SIMS_SampleData_MySQL.sql
  mysql -u root -p < 02_SIMS_Triggers_MySQL.sql

Tai khoan demo (password: 123456):
  admin, salesmgr, invmgr, staff01, staff02
  lan.nguyen, hung.tran, mai.pham, duc.le, customer1

Luu y:
  - File 03 se TRUNCATE toan bo bang roi insert lai: CHI dung cho demo/test.
  - Luon chay file 02 SAU file 03 de tranh trigger nhap kho xu ly trung
    voi du lieu lo hang ma file seed da tao san.
  - File 02 da gom FEFO, tru/hoan dung lo, nhap kho, huy hoa don,
    doi/tra, doi chieu kho, canh bao ton va dong bo gia tu dong.
  - phpMyAdmin la giao dien web, khong phai JDBC URL. App Java can
    MySQL host/port/user/password rieng (thuong port 3306).

Nang cap project dang chay (A1-A3 huy hoa don co phe duyet):
  - Chay 08_INVOICE_CANCEL_APPROVAL.sql SAU khi DB hien tai da ton tai.
  - Dang xuat/dang nhap lai de phien SALES_STAFF nap quyen moi.
