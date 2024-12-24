# Citadel

The Paper plugin that runs the lobby side of Tracked Out.

## Supported Commands

#### Sending events to Dunga Dunga:
| Command | Description | Example | Permission Required |
|---|---|---|---|
| `/log-event <event-name> [count]` | Log any event (this will be stored in MongoDB for later parsing), see [Dunga Dunga events](https://github.com/trackedout/internal-docs/blob/main/infra/README.md) | `/log-event joined-queue 1` | `decked-out.log-event` |

#### Shop Management:
| Command | Description | Example | Permission Required |
|---|---|---|---|
| `/do shop edit` | Shop management | `/do shop edit` | `decked-out.shop.admin` |
| `/do shop rename [name]` | Set shop name | `/do shop rename "Epic Shop"` | `decked-out.shop.admin` |
| `/do shop info` | Show shop info | `/do shop info` | `decked-out.shop.admin` |
| `/do shop disable [x] [y] [z]` | Disable shop at location | `/do shop disable 100 64 200` | `decked-out.shop.admin` |
| `/do shop enable [x] [y] [z]` | Enable shop at location | `/do shop enable 100 64 200` | `decked-out.shop.admin` |

#### Deck Access (deprecated):
| Command | Description | Example | Permission Required |
|---|---|---|---|
| `/gief-shulker` | Fetch your Deck (shulker) from Dunga Dunga and add it to your inventory | `/gief-shulker` | `decked-out.inventory.get-shulker` |
| `/take-shulker` | Remove your Deck (shulker) from your inventory | `/take-shulker` | `decked-out.inventory.return-shulker` |

#### Deck Administration / Testing:
| Command | Description | Example | Permission Required |
|---|---|---|---|
| `/decked-out list-known-cards` | List all known Decked Out 2 cards | `/do list-known-cards` | `decked-out.inventory.list-known` |
| `/decked-out list-cards <player-name>` | List the Decked Out 2 cards in a player's DB inventory | `/do list-cards 4Ply` | `decked-out.inventory.admin` |
| `/decked-out add-card <player-name> <card-name>` | Add Decked Out 2 card into player's DB inventory | `/do add-card 4Ply moment_of_clarity` | `decked-out.inventory.admin` |
| `/decked-out remove-card <player-name> <card-name>` | Remove a Decked Out 2 card from player's DB inventory. If multiple copies exist, only one will be removed | `/do remove-card 4Ply moment_of_clarity` | `decked-out.inventory.admin` |
| `/decked-out remove-all-cards <player-name>` | Remove **ALL** Decked Out 2 cards from player's DB inventory. DANGEROUS! | `/do remove-all-cards 4Ply` | `decked-out.inventory.admin` |
| `/decked-out add-all-known-cards <player-name>` | Add a copy of every known card to a player's DB inventory | `/do add-all-known-cards 4Ply` | `decked-out.inventory.admin` |

#### Artifakes:
| Command | Description | Example | Permission Required |
|---|---|---|---|
| `/do artifakes` | Show artifakes | `/do artifakes` | - |
| `/do artifakes [playerName]` | Show artifakes for target player | `/do artifakes 4Ply` | `decked-out.artifakes.view.all` |


## Setup

Citadel depends on Agronet, so to start, build [Agronet](https://github.com/trackedout/agronet-fabric) using the following command:
```bash
(cd ../agronet-fabric && ./gradlew build)
```

Start a local Minecraft server on port 25575. You may have to edit `./run/eula.txt` and set `eula=true` when running this for the first time.

```bash
./gradlew runServer
```
