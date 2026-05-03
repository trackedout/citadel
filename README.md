# Citadel

The Paper plugin that runs the lobby side of [Tracked Out](https://trackedout.org).

### Changelog

For a list of changes, see the [latest release notes](https://github.com/trackedout/citadel/releases/tag/latest).

## Supported Commands

#### Sending events to Dunga Dunga:
| Command | Description | Permission Required |
|---|---|---|
| `/log-event <event-name> [count]` | Log any event (this will be stored in MongoDB for later parsing), see [Dunga Dunga events](https://github.com/trackedout/internal-docs/blob/main/infra/README.md) | `decked-out.log-event` |

#### Shop Management:
| Command | Description | Permission Required |
|---|---|---|
| `/do shop edit` | Shop management | `decked-out.shop.admin` |
| `/do shop rename [name]` | Set shop name | `decked-out.shop.admin` |
| `/do shop info` | Show shop info | `decked-out.shop.admin` |
| `/do shop disable [x] [y] [z]` | Disable shop at location | `decked-out.shop.admin` |
| `/do shop enable [x] [y] [z]` | Enable shop at location | `decked-out.shop.admin` |

#### Deck Access (deprecated):
| Command | Description | Permission Required |
|---|---|---|
| `/gief-shulker` | Fetch your Deck (shulker) from Dunga Dunga and add it to your inventory | `decked-out.inventory.get-shulker` |
| `/take-shulker` | Remove your Deck (shulker) from your inventory | `decked-out.inventory.return-shulker` |

#### Deck Management:
| Command | Description | Permission Required |
|---|---|---|
| `/do edit` | Open the deck editor GUI | `decked-out.inventory.edit` |
| `/do run-mode <runType>` | Set run mode for inventory filtering | `decked-out.inventory.set-run-mode` |
| `/do shulker-style <color>` | Set the colour of your competitive shulker box | `decked-out.config.shulker-style` |

#### Deck Administration / Testing:
| Command | Description | Permission Required |
|---|---|---|
| `/do list-known-cards` | List all known Decked Out 2 cards | `decked-out.inventory.list-known` |
| `/do list-cards <player>` | List the Decked Out 2 cards in a player's DB inventory | `decked-out.inventory.admin` |
| `/do add-card <player> <card> <runType>` | Add Decked Out 2 card into player's DB inventory | `decked-out.inventory.admin` |
| `/do remove-card <player> <card> <runType>` | Remove a Decked Out 2 card from player's DB inventory | `decked-out.inventory.admin` |
| `/do remove-all-cards <player> <runType>` | Remove **ALL** Decked Out 2 cards from player's DB inventory. DANGEROUS! | `decked-out.inventory.admin` |
| `/do add-all-known-cards <player> <runType>` | Add a copy of every known card to a player's DB inventory | `decked-out.inventory.admin` |
| `/do update-inventory <player>` | Update a player's inventory using DB state | `decked-out.inventory.admin` |

#### Scores:
| Command | Description | Permission Required |
|---|---|---|
| `/do list-scores <player>` | List scoreboard values for a player | `decked-out.inventory.admin` |
| `/do list-shard-costs <player>` | Calculate and list shard cost for a player | `decked-out.inventory.admin` |
| `/do give-item <player> <runType> <item> <count> <reason>` | Update player's score to give them more items | `decked-out.shop.admin` |

#### Config:
| Command | Description | Permission Required |
|---|---|---|
| `/do config show` | Show your current config | `decked-out.config.view` |
| `/do config show <player>` | List config values for target entity | `decked-out.config.view.all` |
| `/do config set <entity> <key> <value>` | Set config for target entity | `decked-out.config.edit` |
| `/do config toggle <key>` | Toggle a config off / on | `decked-out.config.toggle` |

#### Cubbies:
| Command | Description | Permission Required |
|---|---|---|
| `/do cubby claim` | Claim a cubby | - |
| `/do cubby locate` | Locate your cubby | - |
| `/do cubby locate <player>` | Locate a cubby owned by another player | - |
| `/do cubby tp [player]` | Teleport to a player's cubby | - |
| `/do cubby count` | Show counts of owned / available cubbies | - |
| `/do cubby list` | Show all owned cubbies by all players | - |

#### Artifakes:
| Command | Description | Permission Required |
|---|---|---|
| `/do artifakes` | Show artifakes | - |
| `/do artifakes [playerName]` | Show artifakes for target player | `decked-out.artifakes.view.all` |

#### Trophy of Trophies (ToTs):
| Command | Description | Permission Required |
|---|---|---|
| `/tots tp <name>` | Teleport to a trophy | `decked-out.tots.tp` |
| `/tots list <section>` | List all trophies in a section | `decked-out.tots.list` |
| `/tots search <query>` | Search trophies by name, player, section, or description | `decked-out.tots.search` |
| `/tots info` | Get info about the nearest trophy | `decked-out.tots.info` |

#### Queue:
| Command | Description | Permission Required |
|---|---|---|
| `/do queue season-2 [deckId] [player]` | Queue for a season-2 test dungeon | `decked-out.queue.season-2` |

#### Spectating:
| Command | Description | Permission Required |
|---|---|---|
| `/do spectate` | Spectate a player's game | - |

#### Leaderboard:
| Command | Description | Permission Required |
|---|---|---|
| `/do leaderboard clear` | Clear and hide the leaderboard | `decked-out.leaderboard.admin` |
| `/do leaderboard show <animate>` | Show the leaderboard and optionally animate | `decked-out.leaderboard.admin` |
| `/do hardcore-leaderboard` | Show hardcore leaderboard | - |

#### Operations:
| Command | Description | Permission Required |
|---|---|---|
| `/do status` | Toggle debug status scoreboard | `decked-out.inventory.admin` |
| `/do logs` | Toggle operator debug logs | `decked-out.ops.logs` |
| `/do unstuck` | Unstuck yourself | - |
| `/do shutdown-all-empty-dungeons` | Shutdown all empty dungeons | `decked-out.inventory.admin` |

#### Kubernetes Jobs:
| Command | Description | Permission Required |
|---|---|---|
| `/k8s create-snapshot builders` | Create builders snapshot | `decked-out.k8s.schedule-job.snapshot` |
| `/k8s create-snapshot builders2` | Create builders2 snapshot | `decked-out.k8s.schedule-job.snapshot` |
| `/k8s backup-database` | Backup database | `decked-out.k8s.schedule-job.mongo-backup` |


## Setup

Citadel depends on Agronet, so to start, build [Agronet](https://github.com/trackedout/agronet-fabric) using the following command:
```bash
(cd ../agronet-fabric && ./gradlew build)
```

Start a local Minecraft server on port 25575. You may have to edit `./run/eula.txt` and set `eula=true` when running this for the first time.

```bash
./gradlew runServer
```

### Contributors

Thank you to all our contributors for your hard work and support!

<a href="https://github.com/trackedout/citadel/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=trackedout/citadel"/>
</a>
