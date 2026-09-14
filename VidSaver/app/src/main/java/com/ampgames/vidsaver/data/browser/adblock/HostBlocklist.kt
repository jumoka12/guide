package com.ampgames.vidsaver.data.browser.adblock

import com.ampgames.vidsaver.core.net.Urls

/**
 * Immutable set of blocked hosts with parent-domain matching: blocking
 * `ads.example.com` also blocks `a.b.ads.example.com`, but not `example.com`.
 *
 * Lookup is a hash probe per domain label, so matching stays O(labels) even with
 * a list of a hundred thousand hosts.
 */
class HostBlocklist(hosts: Set<String>) {

    private val hosts: Set<String> = hosts

    val size: Int get() = hosts.size

    fun blocksHost(host: String?): Boolean {
        if (host.isNullOrEmpty() || hosts.isEmpty()) return false
        return Urls.hostAndParents(host.lowercase().trimEnd('.')).any { it in hosts }
    }

    fun blocksUrl(url: String): Boolean = blocksHost(Urls.host(url))

    companion object {
        val EMPTY = HostBlocklist(emptySet())

        /**
         * Parses hosts-file syntax: `0.0.0.0 tracker.example.com`, a bare host
         * per line, `#` comments and blank lines. Local entries (`localhost`,
         * `broadcasthost`, loopback addresses) are skipped so a stock hosts file
         * can be dropped in unchanged.
         */
        fun parse(lines: Sequence<String>): HostBlocklist {
            val hosts = HashSet<String>(1024)
            for (rawLine in lines) {
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val tokens = line.split(' ', '\t').filter { it.isNotEmpty() }
                // "0.0.0.0 host" -> take the host; a bare "host" -> take it as is.
                val host = when (tokens.size) {
                    0 -> continue
                    1 -> tokens[0]
                    else -> tokens[1]
                }.trimEnd('.').lowercase()

                if (host.isEmpty() || host in SKIPPED) continue
                if (host.none { it == '.' }) continue // not a domain
                hosts.add(host)
            }
            return HostBlocklist(hosts)
        }

        private val SKIPPED = setOf(
            "localhost",
            "localhost.localdomain",
            "local",
            "broadcasthost",
            "ip6-localhost",
            "ip6-loopback",
            "ip6-localnet",
            "ip6-mcastprefix",
            "ip6-allnodes",
            "ip6-allrouters",
            "ip6-allhosts",
            "0.0.0.0",
            "127.0.0.1",
            "255.255.255.255",
        )
    }
}
