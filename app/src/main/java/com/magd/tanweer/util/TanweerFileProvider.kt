package com.magd.tanweer.util

import androidx.core.content.FileProvider

/**
 * Custom FileProvider subclass for Tanweer to guarantee distinct class resolution
 * and robust meta-data XML resource binding without colliding with any library providers.
 */
class TanweerFileProvider : FileProvider()
