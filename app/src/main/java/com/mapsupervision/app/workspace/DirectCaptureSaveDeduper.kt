package com.mapsupervision.app.workspace

class DirectCaptureSaveDeduper {
    private val savedPaths = mutableSetOf<String>()

    fun saveOnce(path: String, action: () -> Boolean): Boolean {
        synchronized(savedPaths) {
            if (savedPaths.contains(path)) return false
            val success = action()
            if (success) {
                savedPaths.add(path)
            }
            return success
        }
    }
}
