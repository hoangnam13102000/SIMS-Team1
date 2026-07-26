package com.security;

import java.io.File;

/** Hop dong chung cho 1 bo quet file (heuristic noi bo, ClamAV qua clamd, ...). */
public interface VirusScanner {

    ScanResult scan(File file);

    /** Ten hien thi cua bo quet (dung trong log/thong bao), vd "ClamAV", "Heuristic". */
    String getName();
}