**FREE
// ============================================================
// STATDATA - Static Data Maintenance (Subfile Browse/Maintain)
// Purpose  : Browse CUSTODIAN table via subfile; add new rows
// IBM i    : SFL/SFLCTL subfile, EXFMT, page-at-a-time load
// ============================================================

CTL-OPT DFTACTGRP(*NO) ACTGRP('JRAMSRV');

DCL-S wRRN        INT(10) INZ(0);
DCL-S wPageSize   INT(10) INZ(10);
DCL-S wRowsLoaded INT(10) INZ(0);
DCL-S wMoreRows   IND INZ(*ON);

DCL-PR loadSubfile END-PR;

DCL-F STATDATAD WORKSTN SFILE(SFL1:wRRN);

EXEC SQL
  DECLARE C_CUST CURSOR FOR
    SELECT CUST_ID, CUST_NAME, MARKET_CD, COUNTRY_CD, STATUS
    FROM   CODELIVER1.CUSTODIAN
    ORDER  BY CUST_ID;

EXEC SQL OPEN C_CUST;

loadSubfile();

DOW *IN03 = *OFF;

  EXFMT SFLCTL1;
  EXFMT DETAIL1;

  IF *IN03;
    LEAVE;
  ENDIF;

  IF *IN05;
    EXEC SQL CLOSE C_CUST;
    EXEC SQL OPEN C_CUST;
    loadSubfile();
  ENDIF;

  IF %TRIM(NEWCUST_ID) <> *BLANKS;
    EXEC SQL
      INSERT INTO CODELIVER1.CUSTODIAN
        (CUST_ID, CUST_NAME, MARKET_CD, COUNTRY_CD, STATUS)
      VALUES
        (:NEWCUST_ID, :NEWCUST_NM, :NEWMKT_CD, :NEWCTRY_CD, 'A');

    IF SQLCODE = 0;
      NEWCUST_ID = *BLANKS;
      NEWCUST_NM = *BLANKS;
      NEWMKT_CD  = *BLANKS;
      NEWCTRY_CD = *BLANKS;

      EXEC SQL CLOSE C_CUST;
      EXEC SQL OPEN C_CUST;
      loadSubfile();
    ELSE;
      DSPLY 'Insert failed - check job log';
    ENDIF;
  ENDIF;

ENDDO;

EXEC SQL CLOSE C_CUST;

*INLR = *ON;
RETURN;

DCL-PROC loadSubfile;

  DCL-S lCustId   CHAR(6);
  DCL-S lCustName CHAR(40);
  DCL-S lMarketCd CHAR(4);
  DCL-S lCountry  CHAR(2);
  DCL-S lStatus   CHAR(1);

  CLEAR SFL1;
  *IN91 = *ON;
  WRITE SFLCTL1;
  *IN91 = *OFF;

  wRRN = 0;
  wRowsLoaded = 0;
  wMoreRows = *OFF;

  DOU wRowsLoaded >= wPageSize;

    EXEC SQL
      FETCH C_CUST
      INTO :lCustId, :lCustName, :lMarketCd, :lCountry, :lStatus;

    IF SQLCODE = 100;
      LEAVE;
    ENDIF;

    IF SQLCODE < 0;
      DSPLY 'FETCH error - check job log';
      LEAVE;
    ENDIF;

    wRRN += 1;
    wRowsLoaded += 1;

    CUST_ID    = lCustId;
    CUST_NAME  = lCustName;
    MARKET_CD  = lMarketCd;
    COUNTRY_CD = lCountry;
    STATUS     = lStatus;

    WRITE SFL1;
    wMoreRows = *ON;

  ENDDO;

  IF wMoreRows;
    *IN31 = *ON;
  ELSE;
    *IN31 = *OFF;
  ENDIF;

  *IN32 = *ON;

END-PROC;
