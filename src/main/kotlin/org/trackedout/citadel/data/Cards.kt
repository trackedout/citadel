package org.trackedout.citadel.data

class Cards {
    companion object {
        fun cardModelData(cardName: String): Int {
            return when (cardName) {
                "stumble" -> 101
                "sneak" -> 102
                "treasure_hunter" -> 103
                "ember_seeker" -> 104
                "stability" -> 105
                "moment_of_clarity" -> 106
                "pay_to_win" -> 107
                "tactical_approach" -> 108
                "pork_chop_power" -> 109
                "evasion" -> 110
                "loot_and_scoot" -> 111
                "frost_focus" -> 112
                "tread_lightly" -> 113
                "second_wind" -> 114
                "beast_sense" -> 115
                "bounding_strides" -> 116
                "sprint" -> 117
                "smash_and_grab" -> 118
                "reckless_charge" -> 119
                "nimble_looting" -> 120
                "quickstep" -> 121
                "suit_up" -> 122
                "adrenaline_rush" -> 123
                "swagger" -> 124
                "speed_run" -> 125
                "eerie_silence" -> 126
                "chill_step" -> 127
                "dungeon_repairs" -> 128
                "eyes_on_the_prize" -> 129
                "pirate_booty" -> 130
                "cold_snap" -> 131
                "silent_runner" -> 132
                "fuzzy_bunny_slippers" -> 133
                "deepfrost" -> 134
                "brilliance" -> 135
                "boots_of_striding" -> 136
                "glorious_moment" -> 137
                "cash_cow" -> 138
                "avalanche" -> 139
                "beast_master" -> 140
                "dungeon_lackey" -> 141
                else -> {
                    throw Exception("Card model data for $cardName not known")
                }
            }
        }
    }
}