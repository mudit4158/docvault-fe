package com.docvault.app.ui.screens.scan.data

/**
 * Constants from the engineering handoff's client requirements for scan
 * (docvault-be's `scan_to_pdf.md`): page size ~150-200 DPI on a Letter/A4
 * long edge, processed one page at a time to avoid OOM on low-end devices.
 */
object ScanSpec {
    /** Roughly 200 DPI on an A4/Letter long edge (handoff: ~1240x1754-1654x2339px). */
    const val TARGET_LONGEST_SIDE_PX = 1654

    const val WORKING_JPEG_QUALITY = 82

    /** A sane ceiling on one session — also keeps a single exported PDF well under the 20MB upload cap. */
    const val MAX_PAGES = 20
}
