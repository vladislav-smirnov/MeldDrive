package io.github.airdaydreamers.melddrive.data.discovery

import io.github.airdaydreamers.melddrive.data.model.StorageType

data class DiscoveredServer(val name: String, val host: String, val port: Int, val type: StorageType)
