package com.tonymacdonald1995.madhatter

import com.google.gson.Gson
import net.dv8tion.jda.api.EmbedBuilder
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class NicknameData(val id: Long, val nickname: String)

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
    val jda = JDABuilder
        .createDefault(token)
        .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .setChunkingFilter(ChunkingFilter.ALL)
        .addEventListeners(madHatter)
        .build()
    jda.selfUser.manager.setName("Mad Hatter").queue()
}

class MadHatter : ListenerAdapter() {

    override fun onGuildReady(event : GuildReadyEvent) {

        log("Connected to " + event.guild.name)

        event.guild.selfMember.modifyNickname("Mad Hatter").queue()

        event.guild.upsertCommand("nn", "Changes a user's nickname")
            .addOption(OptionType.USER, "user", "User to change nickname", true)
            .addOption(OptionType.STRING, "nickname", "New nickname for the user", true)
            .queue()

        event.guild.upsertCommand("nnbackup", "Creates a backup of nicknames as they currently are.")
            .queue()

        event.guild.upsertCommand("nnrestore", "Restores nicknames from last backup, if one exists.")
            .queue()
    }

    override fun onSlashCommandInteraction(event : SlashCommandInteractionEvent) {

        if (event.guild == null)
            return

        when(event.name) {
            "nn" -> changeNickname(event)
            "nnbackup" -> nicknameBackup(event)
            "nnrestore" -> nicknameRestore(event)
            else -> {
                event.reply("Error: Unknown command").setEphemeral(true).queue()
            }
        }

    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val triggerWords = listOf("change", "swap", "shift", "switch", "trade")
        if (event.message.contentDisplay.containsAny(triggerWords, false))
            nicknameShuffle(event.guild)
        event.channel.sendMessageEmbeds(EmbedBuilder().setDescription("Change Places!").setImage("https://tenor.com/view/futurama-change-places-musical-chairs-gif-14252770").build()).queue()
    }

    private fun changeNickname(event : SlashCommandInteractionEvent) {
        event.deferReply(true).queue()
        val hook = event.hook
        hook.setEphemeral(true)
        val member = event.getOption("user")?.asMember ?: return
        val selfMember = event.guild?.selfMember ?: return
        val nickname = event.getOption("nickname")!!.asString

        if (!selfMember.hasPermission(Permission.NICKNAME_MANAGE)) {
            hook.sendMessage("Error: I don't have permission to manage nicknames.").queue()
            return
        }

        if (member == selfMember) {
            hook.sendMessage("You cannot change my nickname").queue()
            return
        }

        if (event.member?.hasPermission(Permission.NICKNAME_CHANGE) == false && event.member == member) {
            hook.sendMessage("You don't have permission to change your nickname").queue()
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
        hook.sendMessage(member.user.name + "'s nickname has been changed to " + nickname).queue()
        log(event.member?.user?.name + " changed " + member.user.name + "'s nickname to " + nickname)
    }
}

private fun nicknameBackup(event: SlashCommandInteractionEvent) {
    if (event.guild == null) {
        event.reply("Error: Command must be called from a server.").setEphemeral(true).queue()
        return
    }
    val guild = event.guild
    event.deferReply(true).queue()
    val hook = event.hook
    val memberList = guild!!.members.filter {
        it != guild.selfMember && guild.selfMember.canInteract(it)
    }.toMutableList()

    val nicknameMap = memberList.associate { it.idLong to (it.nickname ?: "") }.toMutableMap()
    saveNicknameData(guild.id, nicknameMap)
    hook.sendMessage("Backup made successfully.").queue()
}

private fun nicknameRestore(event: SlashCommandInteractionEvent) {
    if (event.guild == null) {
        event.reply("Error: Command must be called from a server.").setEphemeral(true).queue()
        return
    }

    val guild = event.guild
    event.deferReply(true).queue()
    val hook = event.hook
    val nicknameMap = loadNicknameData(guild!!.id)

    if (nicknameMap.isEmpty()) {
        hook.sendMessage("Error: Unable to restore backup.").queue()
        return
    }

    for(pair in nicknameMap)
        guild.getMemberById(pair.key)?.modifyNickname(pair.value)?.queue()
    hook.sendMessage("Backup restored successfully.").queue()
}

private fun nicknameShuffle(guild: Guild) {
    val memberList = guild.members.filter {
        it != guild.selfMember && guild.selfMember.canInteract(it)
    }.toMutableList()

    val shuffledNicknames = memberList.mapNotNull { it.nickname ?: it.effectiveName }.shuffled()

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

private fun saveNicknameData(guildId: String, nicknameMap: MutableMap<Long, String>) {
    val gson = Gson()
    val memberDataList = nicknameMap.map { NicknameData(it.key, it.value) }
    val json = gson.toJson(memberDataList)
    File("/data/$guildId.json").writeText(json)
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