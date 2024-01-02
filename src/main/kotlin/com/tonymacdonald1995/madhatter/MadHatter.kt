package com.tonymacdonald1995.madhatter

import com.google.gson.Gson
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NicknameData(val id: Long, val nickname: String)

val nicknamePauseMap: MutableMap<Long, Long> = mutableMapOf()

fun main(args : Array<String>) {
    val token = if (args.isNotEmpty())
                    args[0]
                else
                    System.getenv("TOKEN") ?: ""

    if (token.isEmpty()) {
        log("Error: No bot token")
        return
    }
    val madHatter = MadHatter()
    JDABuilder
        .createDefault(token)
        .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .setChunkingFilter(ChunkingFilter.ALL)
        .addEventListeners(madHatter)
        .build()
}

class MadHatter : ListenerAdapter() {

    private val shuffledMap = mutableMapOf<String, Boolean>()

    override fun onGuildReady(event : GuildReadyEvent) {

        log("Connected to " + event.guild.name)

        event.guild.selfMember.modifyNickname("Mad Hatter").queue()

        event.guild.upsertCommand("nnme", "Changes your nickname")
            .addOption(OptionType.STRING, "nickname", "New nickname", false)
            .queue()

        event.guild.upsertCommand("nn", "Changes a user's nickname")
            .addOption(OptionType.USER, "user", "User to change nickname", true)
            .addOption(OptionType.STRING, "nickname", "New nickname for the user", false)
            .queue()

        event.guild.upsertCommand("nnbackup", "Creates a backup of nicknames as they currently are.")
            .queue()

        event.guild.upsertCommand("nnrestore", "Restores nicknames from last backup, if one exists.")
            .queue()
        event.guild.upsertCommand("nnpause", "Pauses nickname shuffle for one hour.")
            .queue()
        event.guild.upsertCommand("nnresume", "Resumes nickname shuffle.")
            .queue()

        shuffledMap[event.guild.id] = false
        nicknameRestore(event)


    }

    override fun onSlashCommandInteraction(event : SlashCommandInteractionEvent) {

        if (event.guild == null)
            return

        when(event.name) {
            "nn" -> changeNickname(event)
            "nnme" -> changeSelfNickname(event)
            "nnbackup" -> nicknameBackup(event)
            "nnrestore" -> nicknameRestore(event)
            "nnpause" -> nicknamePause(event)
            "nnresume" -> nicknameResume(event)
            else -> {
                event.reply("Error: Unknown command").setEphemeral(true).queue()
            }
        }

    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.message.member == event.guild.selfMember)
            return
        if((nicknamePauseMap[event.guild.idLong] ?: 0) > System.currentTimeMillis()) {
            return
        }

        if (shuffledMap[event.guild.id] == true)
            return

        val triggerWords = listOf("change", "swap", "shift", "switch", "trade")
        if (event.message.contentDisplay.containsAny(triggerWords, false)) {
            nicknameShuffle(event.guild)
            event.channel.sendMessage("https://tenor.com/view/futurama-change-places-musical-chairs-gif-14252770").queue()
            shuffledMap[event.guild.id] = true
        }
    }

    private fun changeNickname(event : SlashCommandInteractionEvent) {
        event.deferReply(true).queue()
        val hook = event.hook.setEphemeral(true)
        val member = event.getOption("user")?.asMember ?: return
        val selfMember = event.guild?.selfMember ?: return
        val nickname = event.getOption("nickname")?.asString ?: ""

        if (shuffledMap[event.guild!!.id] == true) {
            hook.sendMessage("Nicknames cannot be changed while they are shuffled, to proceed use /nnrestore").queue()
            return
        }

        if (!selfMember.hasPermission(Permission.NICKNAME_MANAGE)) {
            hook.sendMessage("Error: I don't have permission to manage nicknames.").queue()
            return
        }

        if (member == selfMember) {
            hook.sendMessage("You cannot change my nickname").queue()
            return
        }

        if (!selfMember.canInteract(member)) {
            hook.sendMessage("That user is too powerful for me to change their nickname").queue()
            return
        }

        if (nickname.length > 32) {
            hook.sendMessage("That name is over the max length of 32 characters").queue()
            return
        }

        member.modifyNickname(nickname).queue()
        hook.sendMessage("${member.user.name}'s nickname has been changed to $nickname").queue()
        log("${event.member?.user?.name} changed ${member.user.name}'s nickname to $nickname")
    }

    private fun changeSelfNickname(event: SlashCommandInteractionEvent) {
        event.deferReply(true).queue()
        val hook = event.hook.setEphemeral(true)
        val selfMember = event.guild?.selfMember ?: return
        val member = event.member ?: return
        val nickname = event.getOption("nickname")?.asString ?: ""

        if (shuffledMap[event.guild!!.id] == true) {
            hook.sendMessage("Nicknames cannot be changed while they are shuffled, to proceed use /nnrestore").queue()
            return
        }

        if (selfMember.hasPermission(Permission.NICKNAME_MANAGE)) {
            hook.sendMessage("Error: I don't have permission to manage nicknames.").queue()
            return
        }

        if (!selfMember.canInteract(member)) {
            hook.sendMessage("You are too powerful for me to change your nickname.").queue()
            return
        }

        if (nickname.length > 32) {
            hook.sendMessage("That name is over the max length of 32 characters.").queue()
            return
        }

        member.modifyNickname(nickname).queue()
        hook.sendMessage("Your nickname has been changed to $nickname").queue()
        log("${member.user.name} has changed their nickname to $nickname.")

    }

    private fun nicknameBackup(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return
        val memberList = guild.members.filter {
            it != guild.selfMember && guild.selfMember.canInteract(it)
        }.toMutableList()

        val nicknameMap = memberList.associate { it.idLong to (it.nickname ?: "") }.toMutableMap()
        saveNicknameData(guild.id, nicknameMap)
    }

    private fun nicknameRestore(event: GuildReadyEvent) {
        val nicknameMap = loadNicknameData(event.guild.id)

        if (nicknameMap.isEmpty())
            return
        for (pair in nicknameMap)
            event.guild.getMemberById(pair.key)?.modifyNickname(pair.value)?.queue()
        File("/data/${event.guild.id}.json").delete()
    }

    private fun nicknameRestore(event: SlashCommandInteractionEvent) {
        if (event.guild == null) {
            event.reply("Error: Command must be called from a server.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild ?: return
        event.deferReply(true).queue()
        val hook = event.hook
        val nicknameMap = loadNicknameData(guild.id)

        if (nicknameMap.isEmpty()) {
            hook.sendMessage("Error: Unable to restore backup.").queue()
            return
        }

        for(pair in nicknameMap)
            guild.getMemberById(pair.key)?.modifyNickname(pair.value)?.queue()
        hook.sendMessage("Backup restored successfully.").queue()
        File("/data/${guild.id}.json").delete()
        shuffledMap[guild.id] = false
    }

    private fun nicknameShuffle(guild: Guild) {
        val memberList = guild.members.filter {
            it != guild.selfMember && guild.selfMember.canInteract(it)
        }.toMutableList()

        val shuffledNicknames = memberList.mapNotNull { it.nickname ?: it.user.globalName }.shuffled()

        memberList.zip(shuffledNicknames).forEach { (member, nickname) ->
            member.modifyNickname(nickname).queue()
        }


    }

    private fun loadNicknameData(guildId: String): MutableMap<Long, String> {
        val gson = Gson()
        val file = File("/data/$guildId.json")
        return if (file.exists()) {
            val json = file.readText()
            val memberDataList = gson.fromJson(json, Array<NicknameData>::class.java)
            val nicknameMap = mutableMapOf<Long, String>()
            memberDataList.forEach { nicknameMap[it.id] = it.nickname }
            nicknameMap
        } else {
            mutableMapOf()
        }
    }
private fun nicknamePause(event: SlashCommandInteractionEvent) {
    if (event.guild == null) {
        event.reply("Error: Command must be called from a server.").setEphemeral(true).queue()
        return
    }

    nicknamePauseMap[event.guild!!.idLong] = System.currentTimeMillis() + 60 * 60 * 1000

    val pausedTime = Instant.ofEpochMilli(nicknamePauseMap[event.guild!!.idLong]!!)
    val formatter = DateTimeFormatter.ofPattern("h:mm a zzz").withZone(ZoneId.of("America/Chicago"))

    val formattedPausedTime = formatter.format(pausedTime)

    event.reply("Nickname shuffle has been paused until $formattedPausedTime").setEphemeral(true).queue()
}

private fun nicknameResume(event: SlashCommandInteractionEvent) {
    if (event.guild == null) {
        event.reply("Error: Command must be called from a server.").setEphemeral(true).queue()
        return
    }

    nicknamePauseMap.remove(event.guild!!.idLong)
    event.reply("Nickname shuffle has been resumed.").setEphemeral(true).queue()
}

private fun loadNicknameData(guildId: String): MutableMap<Long, String> {
    val gson = Gson()
    val file = File("/data/$guildId.json")
    return if (file.exists()) {
        val json = file.readText()
        val memberDataList = gson.fromJson(json, Array<NicknameData>::class.java)
        val nicknameMap = mutableMapOf<Long, String>()
        memberDataList.forEach { nicknameMap[it.id] = it.nickname }
        nicknameMap
    } else {
        mutableMapOf()
    }
}

    private fun saveNicknameData(guildId: String, nicknameMap: MutableMap<Long, String>) {
        val gson = Gson()
        val memberDataList = nicknameMap.map { NicknameData(it.key, it.value) }
        val json = gson.toJson(memberDataList)
        File("/data/$guildId.json").writeText(json)
    }
}

fun String.containsAny(list: List<String>, caseSensitive: Boolean = true): Boolean {
    for (item in list) {
        if (this.contains(item, !caseSensitive))
            return true
    }
    return false
}

fun log(message : String) {
    println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + message)
}