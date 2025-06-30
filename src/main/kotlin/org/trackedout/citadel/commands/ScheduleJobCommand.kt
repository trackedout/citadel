package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.client.apis.TasksApi
import org.trackedout.client.models.Task
import org.trackedout.citadel.sendGreyMessage

@CommandAlias("k8s")
class ScheduleJobCommand(
    private val plugin: Citadel,
    private val tasksApi: TasksApi,
) : BaseCommand() {

    @Subcommand("snapshot-dungeons")
    @CommandPermission("decked-out.k8s.schedule-job.snapshot")
    @Description("Snapshot dungeons")
    fun snapshotDungeons(source: CommandSender) {
        plugin.async(source) {
            tasksApi.tasksPost(
                Task(
                    type = "run-job",
                    arguments = listOf(
                        "create-builders-snapshot", "wait-for-builders-snapshot",
                        "upload-backups-to-s3", "wait-for-backup-upload",
                        "import-latest-snapshot-from-s3", "wait-for-snapshot-import"
                    ),
                    server = "job-scheduler",
                )
            )

            source.sendGreyMessage("Scheduled snapshot jobs")
        }
    }

}
