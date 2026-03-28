package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import org.bukkit.command.CommandSender
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.sendGreyMessage
import org.trackedout.client.apis.TasksApi
import org.trackedout.client.models.Task

@CommandAlias("k8s")
class ScheduleJobCommand(
    private val plugin: Citadel,
    private val tasksApi: TasksApi,
) : BaseCommand() {

    @Subcommand("create-snapshot builders")
    @CommandPermission("decked-out.k8s.schedule-job.snapshot")
    @Description("Create builders snapshot")
    fun snapshotB1(source: CommandSender) {
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

    @Subcommand("create-snapshot builders2")
    @CommandPermission("decked-out.k8s.schedule-job.snapshot")
    @Description("Create builders2 snapshot")
    fun snapshotB2(source: CommandSender) {
        plugin.async(source) {
            tasksApi.tasksPost(
                Task(
                    type = "run-job",
                    arguments = listOf(
                        "create-builders2-snapshot", "wait-for-builders2-snapshot",
                        "upload-backups-to-s3", "wait-for-backup-upload",
                    ),
                    server = "job-scheduler",
                )
            )

            source.sendGreyMessage("Scheduled snapshot jobs")
        }
    }

}
