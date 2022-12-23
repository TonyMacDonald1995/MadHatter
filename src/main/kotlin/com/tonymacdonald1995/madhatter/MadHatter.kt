package com.tonymacdonald1995.madhatter

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main(args : Array<String>) {
    val token : String = System.getenv("TOKEN") ?: ""
    if (token.isEmpty()) {
        println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + "Error: No bot token")
        return
    }
    val jda = JDABuilder.createDefault(token).addEventListeners(MadHatter()).build()
    jda.selfUser.manager.setName("Mad Hatter").queue()
}

class MadHatter : ListenerAdapter() {

    override fun onGuildReady(event : GuildReadyEvent) {

        println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + "Connected to " + event.guild.name)

        event.guild.selfMember.modifyNickname("Mad Hatter").queue()

        event.guild.upsertCommand("nn", "Changes a user's nickname")
            .addOption(OptionType.USER, "user", "User to change nickname", true)
            .addOption(OptionType.STRING, "nickname", "New nickname for the user", true)
            .queue()
    }

    override fun onSlashCommandInteraction(event : SlashCommandInteractionEvent) {

        if (event.guild == null)
            return

        when(event.name) {
            "nn" -> {
                changeNickname(event)
            }
            else -> {
                event.reply("Error: Unknown command").setEphemeral(true).queue()
            }
        }

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
        println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + event.member?.user?.name + " changed " + member.user.name + "'s nickname to " + nickname)
    }
}