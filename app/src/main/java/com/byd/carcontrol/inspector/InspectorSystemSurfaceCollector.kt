package com.byd.carcontrol.inspector

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.Locale

/** Best-effort read-only inventory of Android surfaces visible to this app UID. */
class InspectorSystemSurfaceCollector : InspectorCollector {
    override val id = "android.system_surface"
    override val label = "Settings, properties, packages/components, service lists and kernel path inventory"

    override fun collect(context: Context): List<InspectorObservation> = collect(context, includeClassDiscovery = true, includeExpensiveCommands = true)

    fun collect(context: Context, includeClassDiscovery: Boolean, includeExpensiveCommands: Boolean): List<InspectorObservation> = buildList {
        listOf("system" to Settings.System.CONTENT_URI, "secure" to Settings.Secure.CONTENT_URI, "global" to Settings.Global.CONTENT_URI).forEach { (namespace, uri) ->
            try {
                context.contentResolver.query(uri, arrayOf("name", "value"), null, null, "name")?.use { cursor ->
                    val nameCol = cursor.getColumnIndex("name"); val valueCol = cursor.getColumnIndex("value")
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(nameCol) ?: continue
                        add(InspectorObservation(id, "SETTING_SNAPSHOT", cursor.getString(valueCol).orEmpty(), property = "$namespace.$key", metadata = mapOf("namespace" to namespace, "readOnly" to true), isSnapshot = true))
                    }
                }
            } catch (t: Throwable) { add(accessError("settings.$namespace", t)) }
        }
        addAll(runCommand("getprop", "PROPERTY_INVENTORY"))
        val binderInventory = runCommand("service", "SERVICE_MANAGER_INVENTORY", "list")
        addAll(binderInventory)
        val dynamicServiceNames = binderInventory.mapNotNull { observation ->
            Regex("^\\s*\\d+\\s+([^:]+):").find(observation.value.orEmpty())?.groupValues?.getOrNull(1)?.trim()
        }.filter { relevant(it) }.distinct().take(12)
        dynamicServiceNames.filterNot { it in setOf("byd_car_service", "autoservice") }.forEach { serviceName ->
            addAll(runCommand("dumpsys", "DUMPSYS_DISCOVERED_RELEVANT_SERVICE", serviceName))
        }
        addAll(runCommand("dumpsys", "DUMPSYS_SERVICE_INVENTORY", "-l"))
        addAll(runCommand("dumpsys", "DUMPSYS_BYD_CAR_SERVICE", "byd_car_service"))
        addAll(runCommand("dumpsys", "DUMPSYS_AUTOSERVICE", "autoservice"))
        if (includeExpensiveCommands) addAll(runCommand("dumpsys", "DUMPSYS_ACTIVITY_SERVICES", "activity", "services"))
        addAll(runCommand("ps", "BYD_PROCESS_INVENTORY", "-A"))
        addAll(runCommand("lshal", "HAL_SERVICE_INVENTORY"))
        if (includeExpensiveCommands) addAll(runCommand("dmesg", "KERNEL_DMESG"))
        addAll(runCommand("id", "PROCESS_SECURITY_CONTEXT", "-Z"))
        addAll(runCommand("getenforce", "SELINUX_MODE"))

        val pm = context.packageManager
        try {
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
            packages.forEach { info ->
                val packageName = info.packageName
                val permissionNames = info.requestedPermissions.orEmpty().filter { it.contains("BYDAUTO_", true) }
                val app = info.applicationInfo
                if (includeClassDiscovery && isByd(packageName)) scanBydDex(app.sourceDir, packageName).forEach { observation -> add(observation) }
                if (isByd(packageName) || permissionNames.isNotEmpty()) {
                    @Suppress("DEPRECATION")
                    val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                    add(InspectorObservation(id, "PACKAGE_DISCOVERED", packageName, property = packageName, metadata = mapOf("enabled" to app.enabled, "systemApp" to ((app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0), "sourceDir" to app.sourceDir, "versionName" to info.versionName, "versionCode" to version, "bydAutoPermissions" to permissionNames, "packageVisibilityScope" to "packages visible to this UID under Android package visibility policy"), isSnapshot = true))
                }
                val components = buildList {
                    info.services.orEmpty().forEach { if (isByd(packageName) || relevant(it.name)) add("service:${it.name}") }
                    info.receivers.orEmpty().forEach { if (isByd(packageName) || relevant(it.name)) add("receiver:${it.name}") }
                    info.providers.orEmpty().forEach { if (isByd(packageName) || relevant(it.name)) add("provider:${it.name};authority=${it.authority}") }
                }
                components.forEach { component -> add(InspectorObservation(id, "ANDROID_COMPONENT_DISCOVERED", component, service = packageName, metadata = mapOf("package" to packageName), isSnapshot = true)) }
            }
        } catch (t: Throwable) { add(accessError("package_manager_inventory", t, "Package visibility / package manager policy")) }

        val permissions = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).flatMap { it.requestedPermissions.orEmpty() }.filter { it.startsWith("android.permission.BYDAUTO_", true) }.distinct().sorted()
        } catch (t: Throwable) { add(accessError("BYDAUTO_permission_inventory", t)); emptyList() }
        permissions.forEach { permission ->
            val definition = try { pm.getPermissionInfo(permission, 0) } catch (_: Throwable) { null }
            add(InspectorObservation(id, "BYDAUTO_PERMISSION_DISCOVERED", permission, property = permission, metadata = mapOf("protectionLevelRaw" to definition?.protectionLevel, "definitionVisible" to (definition != null)), isSnapshot = true))
        }

        listOf(File("/dev") to 1, File("/sys/class") to 3, File("/sys/bus") to 3, File("/sys/devices/platform") to 3, File("/proc") to 1).forEach { (root, depth) ->
            try {
                val found = mutableListOf<File>()
                var visited = 0
                fun visit(directory: File, remaining: Int, inheritedRelevant: Boolean) {
                    if (visited >= MAX_PATH_VISITED) return
                    val children = directory.listFiles() ?: run {
                        add(InspectorObservation(id, "PATH_ACCESS_DENIED", "listFiles returned null", property = directory.path, metadata = mapOf("errno" to "unknown_or_denied"), isSnapshot = false)); return
                    }
                    children.take((MAX_PATH_VISITED - visited).coerceAtLeast(0)).forEach { child ->
                        visited++
                        val match = inheritedRelevant || relevant(child.name)
                        if (match) found.add(child)
                        if (remaining > 1 && child.isDirectory && (match || directory == root)) visit(child, remaining - 1, match)
                    }
                }
                visit(root, depth, false)
                found.take(500).forEach { child ->
                    val value = if (child.isFile && child.canRead() && child.length() in 1..4096) runCatching { child.readText().trim() }.getOrNull() else null
                    add(InspectorObservation(id, "KERNEL_PATH_DISCOVERED", value ?: if (child.isDirectory) "directory" else if (child.canRead()) "readable" else "not_readable", property = child.path, metadata = mapOf("exists" to child.exists(), "directory" to child.isDirectory, "readable" to child.canRead(), "length" to child.length(), "readOnly" to true), isSnapshot = true))
                }
                if (visited >= MAX_PATH_VISITED || found.size >= MAX_PATH_VISITED) add(InspectorObservation(id, "PATH_ENUMERATION_LIMIT", "Path inventory reached safety limit; further entries were not scanned", property = root.path, metadata = mapOf("truncated" to true, "maxVisited" to MAX_PATH_VISITED), isSnapshot = false))
            } catch (t: Throwable) { add(accessError(root.path, t)) }
        }
    }

    private fun scanBydDex(apkPath: String?, packageName: String): List<InspectorObservation> {
        if (apkPath.isNullOrBlank()) return listOf(InspectorObservation(id, "CLASS_ENUMERATION_BLOCKED", "APK sourceDir unavailable", service = packageName, metadata = mapOf("blocked" to true, "package" to packageName)))
        val apk = File(apkPath)
        if (!apk.canRead()) return listOf(InspectorObservation(id, "CLASS_ENUMERATION_BLOCKED", "APK unreadable to this UID", service = packageName, property = apkPath, metadata = mapOf("blocked" to true, "uid" to android.os.Process.myUid(), "permissionRequirement" to "package/APK read access; system or package policy")))
        val dex = try { dalvik.system.DexFile(apkPath) } catch (t: Throwable) {
            return listOf(InspectorObservation(id, "CLASS_ENUMERATION_BLOCKED", "${t.javaClass.simpleName}: ${t.message.orEmpty()}", service = packageName, property = apkPath, metadata = mapOf("blocked" to true, "exception" to t.javaClass.name, "uid" to android.os.Process.myUid())))
        }
        return try {
            val classes = dex.entries().asSequence().filter { it.startsWith("android.hardware.bydauto.") || it.startsWith("com.byd.auto.") }
                .filter { relevant(it) }.toList()
            classes.map { className -> InspectorObservation(id, "HARDWARE_CLASS_DISCOVERED", className, className = className, service = packageName, property = className,
                metadata = mapOf("apkPackage" to packageName, "apkPath" to apkPath, "loadedOrInitialized" to false, "discoveryMethod" to "DexFile.entries"), isSnapshot = true) }
        } catch (t: Throwable) {
            listOf(InspectorObservation(id, "CLASS_ENUMERATION_BLOCKED", "${t.javaClass.simpleName}: ${t.message.orEmpty()}", service = packageName, property = apkPath, metadata = mapOf("blocked" to true, "exception" to t.javaClass.name, "uid" to android.os.Process.myUid())))
        } finally { try { dex.close() } catch (_: Throwable) { } }
    }

    private fun runCommand(command: String, eventType: String, vararg args: String): List<InspectorObservation> {
        val process = try { ProcessBuilder(listOf(command) + args).redirectErrorStream(true).start() }
        catch (t: Throwable) { return listOf(accessError(command, t, "Executable absent or execution denied")) }
        try {
            val outputBuilder = StringBuilder()
            var truncated = false
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                while (outputBuilder.length < MAX_COMMAND_CHARS) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, MAX_COMMAND_CHARS - outputBuilder.length))
                    if (count < 0) break
                    outputBuilder.append(buffer, 0, count)
                }
                truncated = outputBuilder.length >= MAX_COMMAND_CHARS
            }
            if (truncated) process.destroy()
            val output = outputBuilder.toString()
            val exit = process.waitFor()
            val allLines = output.lineSequence().filter { it.isNotBlank() }.toList()
            val lines = if (command == "ps") allLines.filter { it.contains("byd", true) || it.contains("bydauto", true) } else allLines
            if (lines.isEmpty()) listOf(InspectorObservation(id, eventType, "(no matching output)", service = command, metadata = mapOf("exitCode" to exit, "contentFilter" to (command == "ps")), isSnapshot = true))
            else lines.map { line ->
                val stableKey = when (command) {
                    "getprop" -> Regex("^\\[([^]]+)]").find(line)?.groupValues?.getOrNull(1) ?: line
                    "settings" -> line.substringBefore('=')
                    else -> line.substringBefore(':').take(180).ifBlank { line.take(180) }
                }
                val denied = line.contains("permission denied", true) || line.contains("operation not permitted", true) || line.contains("not permitted", true)
                InspectorObservation(id, if (denied) "SOURCE_ACCESS_ERROR" else eventType, line, service = command, property = stableKey,
                    permission = if (denied) line else null, metadata = mapOf("command" to (listOf(command) + args).joinToString(" "), "exitCode" to exit, "readOnly" to true, "contentFilter" to (command == "ps"), "outputTruncated" to truncated, "blocked" to denied, "permissionRequirement" to if (denied) "not determinable from app UID; see raw denial and SELinux context" else null, "uid" to android.os.Process.myUid()), isSnapshot = !denied)
            }
        } catch (t: Throwable) { try { process.destroy() } catch (_: Throwable) { }; listOf(accessError(command, t)) }
    }

    private fun accessError(source: String, error: Throwable, nextStep: String? = null) = InspectorObservation(id, "SOURCE_ACCESS_ERROR", "${error.javaClass.simpleName}: ${error.message.orEmpty()}", property = source, permission = error.message?.takeIf { it.contains("denied", true) || it.contains("permission", true) }, metadata = mapOf("exception" to error.javaClass.name, "blocked" to true, "permissionRequirement" to "unknown unless specified by the denial", "possibleNextStep" to nextStep, "uid" to android.os.Process.myUid()))
    private fun isByd(name: String) = name.contains("byd", true) || name.contains("bydauto", true)
    private fun relevant(name: String) = KEYWORDS.any { name.lowercase(Locale.ROOT).contains(it) }
    companion object {
        private const val MAX_COMMAND_CHARS = 512_000
        private const val MAX_PATH_VISITED = 500
        private val VOLATILE_VALUE = Regex("time|timestamp|uptime|elapsed|memory|\\bmem\\b|cpu|loadaverage|load_avg", RegexOption.IGNORE_CASE)
        private val KEYWORDS = listOf("byd", "auto", "light", "lamp", "illumination", "ambient", "interior", "room", "dome", "led", "rgb", "body", "vehicle", "car", "hal", "spi", "ivi", "cloud", "ctrl", "control")
    }
}
