@file:Suppress("unused")

package org.Reforward.mirai.plugin


import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.message.data.MessageChain
import java.lang.Exception
import kotlin.math.log


val PluginID = "org.Reforward.mirai-plugin"
val PluginVersion = "0.0.1"

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = PluginID,
        version = PluginVersion
    )
) {
    override fun onEnable() {
        logger.info { "由权益小窝开发组出品。你的全心，我的权益！" }
        Mydata.reload()
        AddSenderId.register()
        AddGroupID.register()
        ForwardtheMsg()
    }

    override fun onDisable() {
        super.onDisable()
        logger.error("插件卸载！")
        AddSenderId.unregister()
        AddGroupID.unregister()
    }


    private fun ForwardtheMsg() {
        subscribeGroupMessages {
            always {
                PluginMain.logger.info("接收到了新的消息！")
                val id: Long = group.id
                val originGroup: Long = Mydata.originGroup
                logger.info("id = ${id}, oringinGroup = ${originGroup}")
                if (id == originGroup && sender.id in Mydata.senderid) {
                    logger.info("准备发送")
                    send(message, bot)
                }
            }
        }
    }


    private fun send(messagechain: MessageChain, bot: Bot) {
        val groups = Mydata.groups
        for (id: Long in groups) {
            launch { bot.getGroup(id).sendMessage(messagechain) }
        }
    }
}

object Mydata : AutoSavePluginConfig("Groups") {
    var groups: MutableSet<Long> by value(mutableSetOf<Long>())
    var senderid: MutableSet<Long> by value(mutableSetOf<Long>())
    var originGroup: Long by value(445786154L)
}

object AddSenderId : SimpleCommand(
    PluginMain, "addSender",
    description = "添加允许转发的人的QQ号",
) {
    @Handler
    suspend fun CommandSender.addSender(Id: Long) {
        if (!Mydata.senderid.add(Id)) {
            PluginMain.logger.error("你已经添加过这名小窝了")
        } else {
            PluginMain.logger.info("添加小窝成功！")
        }
    }
}

object AddGroupID : SimpleCommand(
    PluginMain, "AddGroup",
    description = "添加转发的群",
) {
    @Handler
    suspend fun CommandSender.addGroup(Id: Long) {
        if (!Mydata.groups.add(Id)) {
            PluginMain.logger.error("你已经添加过这群了！")
        } else {
            PluginMain.logger.info("添加转发的群成功！")
        }
    }
}