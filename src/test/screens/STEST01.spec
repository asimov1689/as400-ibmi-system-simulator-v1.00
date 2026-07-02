# STEST01 - System Test: STATDATA interactive screen (header + subfile together)
# Scope: the one thing UTEST01/ITEST01A cannot reach - the actual EXFMT/DDS
# rendering cycle. Run with: java -cp <classpath> ScreenTest STEST01.spec
program: STATDATA
library: CODELIVER1
expect: Static Data Maintenance
expect: Cust ID
expect: Name
expect: CU0001
expect: CU0002
expect: CU0003
expect: CU0004
