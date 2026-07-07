package com.polyalerts.ui

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * ZXing's default scanner opens in landscape, which looks like a clunky barcode reader.
 * This subclass exists only so the manifest can pin the scanner to portrait.
 */
class PortraitCaptureActivity : CaptureActivity()
