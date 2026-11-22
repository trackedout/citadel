package org.trackedout.citadel.cubby

private const val CONFIRM_FLAG = "--confirm"

/** Trailing `--name` / `--name value` arguments of a cubby command. Without `--confirm` a command only reports. */
class CubbyFlags(private val args: List<String>) {
    val confirmed: Boolean get() = CONFIRM_FLAG in args

    fun value(name: String): String? = args.indexOf("--$name").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

    fun intValue(name: String): Int? = value(name)?.toIntOrNull()

    companion object {
        fun of(args: Array<String>?) = CubbyFlags(args?.toList().orEmpty())
    }
}
