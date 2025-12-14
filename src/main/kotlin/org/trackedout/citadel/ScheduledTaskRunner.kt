package org.trackedout.citadel

import com.google.common.io.ByteStreams
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.client.apis.TasksApi
import org.trackedout.client.models.Task
import org.trackedout.client.models.TasksIdPatchRequest

// WARNING: This task is processed *asynchronously*, and thus any interaction with the main Minecraft thread
// must go through a sync task scheduler
class ScheduledTaskRunner(
    private val plugin: Citadel,
    private val tasksApi: TasksApi,
    private val inventoryManager: InventoryManager,
) : BukkitRunnable() {
    override fun run() {
        plugin.debug("[Async task ${this.taskId}] Fetching scheduled commands from Dunga Dunga")
        val tasks = tasksApi.tasksGet(
            server = plugin.serverName,
            limit = 10,
            state = "SCHEDULED",
        ).results!!

        tasks.forEach {
            plugin.logger.info("[Async task ${this.taskId}] Handling task: $it")
            it.updateState(tasksApi, "IN_PROGRESS")
            handleTaskOnNextTick(it)
        }
    }

    private fun handleTaskOnNextTick(task: Task) {
        object : BukkitRunnable() {
            override fun run() {
                try {
                    // Handle task
                    plugin.logger.info("Handling task: $task")
                    when (task.type) {
                        "bungee-message" -> {
                            if (task.targetPlayer != null) {
                                val targetPlayer = plugin.server.worlds.find { it.name == "world" }?.players?.find { it.name == task.targetPlayer }
                                if (targetPlayer != null) {
                                    val out = ByteStreams.newDataOutput()
                                    task.arguments?.forEach(out::writeUTF)
                                    targetPlayer.sendPluginMessage(plugin, "BungeeCord", out.toByteArray())
                                } else {
                                    val message = "Task type is '${task.type}' and targets a player, but the player was not found"
                                    plugin.logger.warning(message)
                                    throw Exception(message)
                                }
                            } else {
                                val out = ByteStreams.newDataOutput()
                                task.arguments?.forEach(out::writeUTF)
                                plugin.server.sendPluginMessage(plugin, "BungeeCord", out.toByteArray())
                            }
                        }

                        "execute-command" -> {
                            runCommandsOnSubsequentTicks(ArrayDeque(task.arguments!!))
                        }

                        "message-player" -> {
                            executeOnPlayer(task) { player ->
                                task.arguments?.forEach(player::sendMiniMessage)
                            }
                        }

                        "send-title" -> {
                            executeOnPlayer(task) { player ->
                                var title = task.arguments?.getOrNull(0) ?: ""
                                var subtitle = task.arguments?.getOrNull(1) ?: ""

                                if (title.isEmpty() && subtitle.isEmpty()) {
                                    val message = "Task type is '${task.type}' but no title or subtitle was provided"
                                    plugin.logger.warning(message)
                                    throw Exception(message)
                                }

                                // Wrap in default colours
                                title = "<aqua>${title}</aqua>"
                                subtitle = "<red>${subtitle}</red>"

                                val mm = MiniMessage.miniMessage()
                                player.showTitle(
                                    Title.title(
                                        mm.deserialize(title).asComponent(),
                                        mm.deserialize(subtitle).asComponent()
                                    )
                                )
                            }
                        }

                        "play-sound" -> {
                            executeOnPlayer(task) { player ->
                                task.arguments?.forEach { sound ->
                                    player.playSound(player.location, sound, 1f, 1f)
                                }
                            }
                        }

                        "update-inventory" -> {
                            executeOnPlayer(task) { player ->
                                inventoryManager.updateInventoryBasedOnScore(player)
                            }
                        }

                        "message-ops" -> {
                            val targetPlayers = plugin.server.worlds.find { it.name == "world" }?.players?.filter { it.scoreboardTags.contains("debug") }
                            targetPlayers?.forEach {
                                task.arguments?.forEach(it::sendGreyMessage)
                            }
                        }

                        "broadcast-message" -> {
                            plugin.server.worlds.find { it.name == "world" }?.let { world ->
                                task.arguments?.forEach { message ->
                                    world.sendMessage(Component.text().color(NamedTextColor.DARK_AQUA).content(message).build())
                                }
                            }
                        }

                        else -> throw Exception("Unknown command type '${task.type}'")
                    }

                    plugin.logger.info("Successfully handled task: $task")
                    task.updateStateAsync(plugin, tasksApi, "SUCCEEDED")
                } catch (e: Exception) {
                    plugin.logger.severe("Failed to handle task: $task")
                    e.printStackTrace()
                    task.updateStateAsync(plugin, tasksApi, "FAILED")
                }
            }
        }.runTask(plugin)
    }

    private fun executeOnPlayer(task: Task, handler: (player: Player) -> Unit) {
        val player = plugin.server.worlds.find { it.name == "world" }?.players?.find { it.name == task.targetPlayer }
        if (player != null) {
            handler(player)
        } else {
            val message = "Task type is '${task.type}' which targets a player, but the player was not found"
            plugin.logger.warning(message)
            throw Exception(message)
        }
    }

    private fun runCommandsOnSubsequentTicks(commands: ArrayDeque<String>) {
        object : BukkitRunnable() {
            override fun run() {
                if (commands.isEmpty()) {
                    return
                }

                val command = commands.removeFirst()
                try {
                    plugin.logger.info("Dispatching command: $command")
                    plugin.server.dispatchCommand(plugin.server.consoleSender, command)

                    runCommandsOnSubsequentTicks(commands)
                } catch (e: Exception) {
                    plugin.logger.severe("Failed to execute command: $command")
                    e.printStackTrace()
                }
            }
        }.runTask(plugin)
    }
}

fun Task.updateState(api: TasksApi, state: String) {
    api.tasksIdPatch(this.id!!, TasksIdPatchRequest(state))
}

fun Task.updateStateAsync(plugin: Citadel, api: TasksApi, state: String) {
    val task = this
    object : BukkitRunnable() {
        override fun run() {
            api.tasksIdPatch(task.id!!, TasksIdPatchRequest(state))
        }
    }.runTaskAsynchronously(plugin)
}
