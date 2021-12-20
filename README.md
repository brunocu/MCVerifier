# MCVerifier

[![GitHub license](https://img.shields.io/github/license/brunocu/MCVerifier)](https://github.com/brunocu/MCVerifier/blob/main/LICENSE)
[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/brunocu/MCVerifier/Build%20and%20release?logo=github)](https://github.com/brunocu/MCVerifier/actions/workflows/release.yml)

Minecraft [Paper](https://papermc.io/) plugin for discord user verification. Connect to a custom discord bot and sync roles with minecraft permissions for verified users.

I made this for myself out of need and for fun, but feel free to use and PR. Subject to specs changes without notice, but I'll try not to break previous releases. No guarantees for snapshot releases.

## Requirements

- [Vault](https://dev.bukkit.org/projects/vault) Permissions API.
- A Permissions plugin that supports groups, I recommend [LuckPerms](https://luckperms.net/).

## Install

Download the latest jar from [releases](https://github.com/brunocu/MCVerifier/releases) and put it in your `plugins` folder.

## Build

Paper API [requires at least Java 16](https://papermc.io/forums/t/java-16-mc-1-17-and-paper/5615) to build. Just clone and run `mvn package`.

## License 

Licensed under the permissive MIT license. Please see [LICENSE](https://github.com/brunocu/MCVerifier/blob/main/LICENSE) for more info.
