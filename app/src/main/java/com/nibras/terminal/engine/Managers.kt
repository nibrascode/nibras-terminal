package com.nibras.terminal.engine

data class Environment(val id: String, val distribution: String, val installed: Boolean)

data class Proc(val id: Int, val command: String)

class EngineNotReady(message: String) : IllegalStateException(message)

interface EnvironmentManager {
    fun listEnvironments(): List<Environment>
    fun installEnvironment(distribution: String)
    fun startEnvironment(environmentId: String)
    fun stopEnvironment(environmentId: String)
    fun restartEnvironment(environmentId: String)
    fun removeEnvironment(environmentId: String)
    fun getEnvironmentStatus(environmentId: String): String
    fun getDiskUsage(environmentId: String): Long
    fun backupEnvironment(environmentId: String): String
    fun restoreEnvironment(backupFile: String)
}

interface ProcessManager {
    fun startProcess(command: String, workingDirectory: String): Int
    fun stopProcess(processId: Int)
    fun listProcesses(): List<Proc>
    fun streamOutput(processId: Int): String
}

interface LinuxPackageManager {
    fun searchPackages(query: String): List<String>
    fun installPackage(packageName: String)
    fun removePackage(packageName: String)
    fun updatePackageIndex()
    fun upgradePackages()
    fun listInstalledPackages(): List<String>
}

interface ProjectManager {
    fun createProject(template: String, name: String): String
    fun importProject(uri: String): String
    fun exportProject(projectId: String, destination: String): String
    fun openProject(projectId: String): String
    fun deleteProject(projectId: String)
}

interface ServerManager {
    fun startServer(projectId: String, host: String, port: Int): Int
    fun stopServer(serverId: Int)
    fun listServers(): List<Int>
    fun getServerLogs(serverId: Int): String
}

private const val NOT_READY =
    "Bu köhnə interfeys sessiya açmır. Sessiya ekranı açılmış mühitdə bir əmr işlədir."

class PendingEnvironmentManager(
    private val installedRootfs: List<Environment>,
) : EnvironmentManager {
    override fun listEnvironments(): List<Environment> = installedRootfs
    override fun installEnvironment(distribution: String) {
        throw EngineNotReady("Mühiti Yükləmələr ekranından seç. Avtomatik quraşdırma yoxdur.")
    }
    override fun startEnvironment(environmentId: String) = throw EngineNotReady(NOT_READY)
    override fun stopEnvironment(environmentId: String) = throw EngineNotReady(NOT_READY)
    override fun restartEnvironment(environmentId: String) = throw EngineNotReady(NOT_READY)
    override fun removeEnvironment(environmentId: String) {
        throw EngineNotReady("Silməni Yükləmələr ekranından təsdiqlə.")
    }
    override fun getEnvironmentStatus(environmentId: String): String {
        val found = installedRootfs.any { it.id == environmentId && it.installed }
        return if (found) "arxiv açılıb" else "yüklənməyib"
    }
    override fun getDiskUsage(environmentId: String): Long =
        installedRootfs.firstOrNull { it.id == environmentId }?.let { 0L } ?: 0L
    override fun backupEnvironment(environmentId: String): String = throw EngineNotReady(NOT_READY)
    override fun restoreEnvironment(backupFile: String) = throw EngineNotReady(NOT_READY)
}

class PendingProcessManager : ProcessManager {
    override fun startProcess(command: String, workingDirectory: String): Int = throw EngineNotReady(NOT_READY)
    override fun stopProcess(processId: Int) = throw EngineNotReady(NOT_READY)
    override fun listProcesses(): List<Proc> = emptyList()
    override fun streamOutput(processId: Int): String = throw EngineNotReady(NOT_READY)
}

class PendingLinuxPackageManager : LinuxPackageManager {
    override fun searchPackages(query: String): List<String> = throw EngineNotReady(NOT_READY)
    override fun installPackage(packageName: String) = throw EngineNotReady(NOT_READY)
    override fun removePackage(packageName: String) = throw EngineNotReady(NOT_READY)
    override fun updatePackageIndex() = throw EngineNotReady(NOT_READY)
    override fun upgradePackages() = throw EngineNotReady(NOT_READY)
    override fun listInstalledPackages(): List<String> = emptyList()
}

class LocalProjectManager : ProjectManager {
    override fun createProject(template: String, name: String): String =
        throw EngineNotReady("Şablonu Yükləmələr ekranından endir, sonra Fayllar bölməsində aç.")
    override fun importProject(uri: String): String = throw EngineNotReady("İdxal bu versiyada bağlıdır.")
    override fun exportProject(projectId: String, destination: String): String =
        throw EngineNotReady("İxrac bu versiyada bağlıdır.")
    override fun openProject(projectId: String): String = projectId
    override fun deleteProject(projectId: String) = throw EngineNotReady("Silmə təsdiqsiz icra olunmur.")
}

class PendingServerManager : ServerManager {
    override fun startServer(projectId: String, host: String, port: Int): Int {
        if (host != "127.0.0.1") throw EngineNotReady("Server yalnız 127.0.0.1 üzərində açıla bilər.")
        throw EngineNotReady(NOT_READY)
    }
    override fun stopServer(serverId: Int) = throw EngineNotReady(NOT_READY)
    override fun listServers(): List<Int> = emptyList()
    override fun getServerLogs(serverId: Int): String = ""
}
