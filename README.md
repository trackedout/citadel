# Citadel

The Paper plugin that runs the lobby side of Tracked Out

### Usage

Citadel depends on Agronet, so to start, build [Agronet](https://github.com/trackedout/agronet-fabric) using the following command:
```bash
(cd ../agronet-fabric && ./gradlew build)
```

Start a local Minecraft server on port 25575. You may have to edit ./run/eula.txt and set `eula=true` when running this for the first time.

```bash
./gradlew runServer
```
