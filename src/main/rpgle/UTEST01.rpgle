**FREE
// UTEST01 - Unit Test: STATDATA subfile load counting
// Pattern: Arrange -> Act -> Assert
CTL-OPT DFTACTGRP(*NO) ACTGRP('JRAMSRV');

// QMHSNDPM: sends messages to job log (visible in DSPJOBLOG)
DCL-PR QMHSNDPM EXTPGM('QMHSNDPM');
  msgId    CHAR(7)   CONST;
  msgFile  CHAR(20)  CONST;
  msgData  CHAR(256) CONST;
  msgLen   INT(10)   CONST;
  msgType  CHAR(10)  CONST;
  stackE   CHAR(10)  CONST;
  stackC   INT(10)   CONST;
  msgKey   CHAR(4);
  errDs    CHAR(256);
END-PR;

DCL-S wRRN     INT(10);
DCL-S wPassed  INT(10) INZ(0);
DCL-S wFailed  INT(10) INZ(0);
DCL-S dispMsg  CHAR(52);
DCL-S msgKey   CHAR(4);
DCL-S errDs    CHAR(256) INZ(*ALLX'00');
DCL-S lCustId   CHAR(6);
DCL-S lCustName CHAR(40);
DCL-S lMarketCd CHAR(4);
DCL-S lCountry  CHAR(2);
DCL-S lStatus   CHAR(1);

// TC-U-01: seed data yields exactly 4 rows
EXEC SQL
  DECLARE C_CUST CURSOR FOR
    SELECT CUST_ID, CUST_NAME, MARKET_CD, COUNTRY_CD, STATUS
    FROM   CODELIVER1.CUSTODIAN ORDER BY CUST_ID;
EXEC SQL OPEN C_CUST;
wRRN = 0;
DOW SQLCODE = 0;
  EXEC SQL FETCH C_CUST
    INTO :lCustId, :lCustName, :lMarketCd, :lCountry, :lStatus;
  IF SQLCODE = 100;
    LEAVE;
  ENDIF;
  IF SQLCODE < 0;
    LEAVE;
  ENDIF;
  wRRN += 1;
ENDDO;
EXEC SQL CLOSE C_CUST;

IF wRRN = 4;
  dispMsg = 'TC-U-01 PASS: 4 custodian rows counted correctly';
  wPassed += 1;
ELSE;
  dispMsg = 'TC-U-01 FAIL: expected 4, got ' + %CHAR(wRRN);
  wFailed += 1;
ENDIF;
CALLP QMHSNDPM('       ':'                    ':dispMsg:
               %LEN(%TRIMR(dispMsg)):'*INFO     ':'*PGMBDY   ':1:msgKey:errDs);

// TC-U-02: empty filtered set yields 0 rows
EXEC SQL
  DECLARE C_EMPTY CURSOR FOR
    SELECT CUST_ID, CUST_NAME, MARKET_CD, COUNTRY_CD, STATUS
    FROM   CODELIVER1.CUSTODIAN WHERE STATUS = 'Z';
EXEC SQL OPEN C_EMPTY;
wRRN = 0;
DOW SQLCODE = 0;
  EXEC SQL FETCH C_EMPTY
    INTO :lCustId, :lCustName, :lMarketCd, :lCountry, :lStatus;
  IF SQLCODE = 100;
    LEAVE;
  ENDIF;
  IF SQLCODE < 0;
    LEAVE;
  ENDIF;
  wRRN += 1;
ENDDO;
EXEC SQL CLOSE C_EMPTY;

IF wRRN = 0;
  dispMsg = 'TC-U-02 PASS: empty result set correctly counts 0';
  wPassed += 1;
ELSE;
  dispMsg = 'TC-U-02 FAIL: expected 0, got ' + %CHAR(wRRN);
  wFailed += 1;
ENDIF;
CALLP QMHSNDPM('       ':'                    ':dispMsg:
               %LEN(%TRIMR(dispMsg)):'*INFO     ':'*PGMBDY   ':1:msgKey:errDs);

dispMsg = 'UNIT SUMMARY (UTEST01): ' + %CHAR(wPassed) +
          ' passed, ' + %CHAR(wFailed) + ' failed';
CALLP QMHSNDPM('       ':'                    ':dispMsg:
               %LEN(%TRIMR(dispMsg)):'*INFO     ':'*PGMBDY   ':1:msgKey:errDs);
*INLR = *ON;
RETURN;
