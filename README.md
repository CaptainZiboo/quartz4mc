# Quartz4MC

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-blue)
![License](https://img.shields.io/badge/License-MIT-green)

**Quartz4MC** (Quartz for Minecraft) is a Fabric mod that allows you to schedule and automatically execute Minecraft commands using cron expressions. Built on top of the Quartz Scheduler for Java, it brings robust, flexible task scheduling to your Minecraft server.

## Features

- Schedule commands using standard **Quartz cron expressions**
- Enable/disable tasks dynamically
- Full command management with `/quartz4mc`
- Built-in suggestions for common cron patterns
- Automatic failure handling: disables a task after 5 consecutive failures
- Logs execution status and errors in server console

## Installation

1. Make sure **Fabric** is installed on your Minecraft server.
2. Place the `Quartz4MC-<version>.jar` file in the `mods` folder.
3. Restart your server.
4. The configuration file will be generated at `config/quartz4mc.json`.

## Commands

```text
/quartz4mc reload
    Reload the configuration file

/quartz4mc list [enabled|disabled]
    List all tasks, optionally filtering by enabled or disabled

/quartz4mc add <id> <pattern> <command>
    Add a new cron task

/quartz4mc remove <id>
    Remove a cron task

/quartz4mc start <id>
    Enable and schedule a cron task

/quartz4mc stop <id>
    Disable a cron task

/quartz4mc details <id>
    Display detailed information about a task

/quartz4mc status
    Show the scheduler's status and total crons
```

- `id` : unique identifier for the task
- `pattern` : cron expression (Quartz 6-field format)
- `command` : Minecraft command to execute
- `enabled` : whether the task is active

## Configuration

- The configuration file is generated at `config/quartz4mc.json`.
- Example of a default cron entry:

```json
{
  "id": "example_broadcast",
  "schedule": "0 * * * * ?",
  "command": "tellraw @a {\"text\":\"[Quartz4MC] Edit config/quartz4mc.json to disable this default cron.\",\"color\":\"gold\"}",
  "enabled": true
}
```

## Scheduler and Logging

- Quartz4MC is based on **Quartz Scheduler for Java**, ensuring reliable and precise execution of scheduled commands.
- Failed tasks are logged in the server console.
- After **5 consecutive failures**, a task is automatically disabled to prevent infinite error loops.

## Development and Contributions

- Built with **Fabric API** for Minecraft
- Open source and available under the **MIT License**
- Contributions are welcome! Fork the repository and submit pull requests.

## License

Quartz4MC is licensed under the **MIT License**.
See the [LICENSE](LICENSE) file for details.

## Credits

- Developed by [CaptainZiboo](https://github.com/CaptainZiboo)
- Built on top of the Quartz Scheduler Java library
- Adapted for Minecraft Fabric servers
