package com.cyberpulse.app.domain

enum class NewsCategory(val label: String) {
    VULNERABILITIES("Vulnerabilities"),
    MALWARE("Malware & Ransomware"),
    BREACHES("Breaches & Incidents"),
    THREAT_ACTORS("Threat Actors & Campaigns"),
    POLICY("Policy & Law Enforcement"),
    RESEARCH("Research & Tools"),
    INDUSTRY("Industry"),
    GENERAL("General"),
}

enum class ItemKind {
    /** A news article from an RSS/Atom feed. */
    NEWS,
    /** A CVE record from NVD and/or the CISA KEV catalog. */
    CVE,
    /** A vendor or government security advisory (e.g. CISA). */
    ADVISORY,
}

enum class Severity(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical");

    companion object {
        fun fromScore(score: Double?): Severity? = when {
            score == null -> null
            score >= 9.0 -> CRITICAL
            score >= 7.0 -> HIGH
            score >= 4.0 -> MEDIUM
            score > 0.0 -> LOW
            else -> null
        }
    }
}

/** How the user wants vulnerabilities affecting a [SystemType] treated. */
enum class WatchLevel(val label: String) {
    FLAGGED("Flag"),
    DEFAULT("Default"),
    SUPPRESSED("Suppress"),
}

enum class NotifyMode(val label: String) {
    FLAGGED_ONLY("Flagged systems only"),
    ALL_EXCEPT_SUPPRESSED("Everything except suppressed"),
}

/**
 * Broad families of affected systems. Items are tagged by keyword matching on their
 * text and, for CVE records, on the CPE vendor/product names NVD attaches.
 */
enum class SystemType(
    val label: String,
    val description: String,
    val keywords: List<String>,
    /** Phrases removed from the text before matching, to avoid false positives. */
    val excludes: List<String> = emptyList(),
) {
    WINDOWS(
        "Windows", "Microsoft Windows desktop & server, Active Directory",
        listOf("windows", "win32", "ntlm", "active directory", "kerberos", "smb", "smbv1", "smbv3", "rdp",
            "remote desktop", "powershell", "patch tuesday", "windows server", "win11", "win10"),
    ),
    MACOS(
        "macOS", "Apple Mac computers",
        listOf("apple", "macos", "mac os", "os x", "macbook", "imac", "mac mini", "xprotect"),
    ),
    LINUX(
        "Linux & Unix", "Linux distributions, the kernel, core Unix tooling",
        listOf("linux", "ubuntu", "debian", "red hat", "rhel", "centos", "fedora", "suse", "opensuse",
            "alpine linux", "glibc", "sudo", "systemd", "openssh", "openssl", "freebsd", "openbsd", "unix", "polkit"),
    ),
    ANDROID(
        "Android", "Android phones, tablets and apps",
        listOf("android", "pixel", "samsung galaxy", "google play", "apk", "qualcomm", "mediatek"),
    ),
    IOS(
        "iOS & iPadOS", "iPhone, iPad, Apple Watch, Vision Pro",
        listOf("apple", "ios", "ipados", "iphone", "ipad", "watchos", "visionos", "tvos"),
        excludes = listOf("cisco ios", "ios xe", "ios xr", "ios-xe", "ios-xr", "ios software", "ios_xe", "ios_xr"),
    ),
    BROWSERS(
        "Web Browsers", "Chrome, Firefox, Safari, Edge and extensions",
        listOf("chrome", "chromium", "firefox", "safari", "webkit", "microsoft edge", "edge browser",
            "opera browser", "brave browser", "browser", "browsers", "v8", "browser extension"),
    ),
    NETWORK(
        "Network & Edge Devices", "Routers, firewalls, VPNs, load balancers",
        listOf("router", "routers", "firewall", "firewalls", "vpn", "vpns", "cisco", "fortinet", "fortigate",
            "fortios", "fortiweb", "fortimanager", "palo alto networks", "pan-os", "globalprotect", "juniper",
            "junos", "sonicwall", "ivanti connect secure", "ivanti", "pulse secure", "netscaler", "citrix adc",
            "big-ip", "f5", "mikrotik", "zyxel", "tp-link", "netgear", "d-link", "ubiquiti", "draytek",
            "check point", "aruba", "sd-wan", "ios xe", "ios xr", "wi-fi", "wifi", "access point", "fortimail", "fortiproxy", "fortisiem",
            "fortianalyzer", "fortiswitch", "vpn gateway"),
    ),
    CLOUD(
        "Cloud & SaaS", "AWS, Azure, Google Cloud, Kubernetes, SaaS platforms",
        listOf("aws", "amazon web services", "azure", "entra id", "google cloud", "gcp", "kubernetes", "k8s",
            "docker", "container", "containers", "s3 bucket", "saas", "microsoft 365", "office 365", "okta",
            "salesforce", "snowflake", "cloudflare", "serverless", "terraform"),
    ),
    VIRTUALIZATION(
        "Virtualization", "VMware, Hyper-V, Proxmox, hypervisors",
        listOf("vmware", "esxi", "vcenter", "vsphere", "hyper-v", "virtualbox", "proxmox", "xen", "qemu",
            "hypervisor", "virtual machine", "kvm"),
    ),
    WEB_APPS(
        "Web Servers & Apps", "WordPress, CMSs, web servers, web frameworks",
        listOf("wordpress", "drupal", "joomla", "magento", "woocommerce", "nginx", "apache http server",
            "apache httpd", "tomcat", "php", "iis", "web server", "web application", "web app", "cms",
            "sql injection", "cross-site scripting", "xss", "csrf", "next.js", "node.js", "django", "laravel",
            "struts", "spring framework", "spring boot"),
    ),
    DATABASES(
        "Databases", "SQL and NoSQL database engines",
        listOf("database", "databases", "mysql", "postgresql", "postgres", "mongodb", "redis", "oracle database",
            "sql server", "mssql", "elasticsearch", "mariadb", "sqlite", "cassandra"),
    ),
    ENTERPRISE(
        "Enterprise Software", "Office, Exchange, SAP, Atlassian, file transfer, backup",
        listOf("exchange server", "microsoft exchange", "sharepoint", "microsoft office", "outlook", "excel",
            "sap", "netweaver", "oracle e-business", "weblogic", "atlassian", "confluence", "jira", "moveit",
            "goanywhere", "solarwinds", "servicenow", "veeam", "zimbra", "microsoft teams", "zoom", "slack",
            "citrix", "connectwise", "screenconnect", "kaseya", "erp", "crm", "file transfer"),
    ),
    DEV_TOOLS(
        "Developer Tools & Supply Chain", "npm, PyPI, GitHub, CI/CD, open-source packages",
        listOf("npm", "pypi", "rubygems", "crates.io", "maven", "nuget", "github", "gitlab", "bitbucket",
            "jenkins", "supply chain", "supply-chain", "open-source package", "open source package",
            "vs code", "visual studio code", "visual studio", "ci/cd", "git", "docker hub", "package manager",
            "python package", "javascript library"),
    ),
    ICS_OT(
        "Industrial & OT", "SCADA, PLCs, industrial control systems",
        listOf("ics", "scada", "plc", "plcs", "industrial control", "operational technology", "siemens",
            "schneider electric", "rockwell", "allen-bradley", "hmi", "modbus", "mitsubishi electric",
            "honeywell", "abb", "emerson", "moxa", "delta electronics", "icsa", "water utility", "power grid"),
    ),
    IOT(
        "IoT & Embedded", "Cameras, NAS, smart devices, printers, firmware",
        listOf("iot", "internet of things", "smart home", "camera", "cameras", "ip camera", "dvr", "nvr", "nas",
            "qnap", "synology", "realtek", "hikvision", "dahua", "smart tv", "printer", "printers", "firmware",
            "bluetooth", "embedded device", "embedded devices", "wearable"),
    ),
    AI(
        "AI & ML Systems", "LLM apps, AI agents, ML frameworks",
        listOf("llm", "llms", "large language model", "chatgpt", "openai", "copilot", "ai agent", "ai agents",
            "generative ai", "genai", "prompt injection", "mcp server", "machine learning", "ollama",
            "langchain", "hugging face", "pytorch", "tensorflow"),
    ),
}
