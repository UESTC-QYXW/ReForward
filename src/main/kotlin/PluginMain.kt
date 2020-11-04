@file:Suppress("unused")

package org.Reforward.mirai.plugin


import com.google.auto.service.AutoService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.console.util.ContactUtils.getContact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.*
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.recall
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


val PluginID = "org.Reforward.mirai-plugin"
val PluginVersion = "0.0.8"


@AutoService(KotlinPlugin::class)
object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = PluginID,
        version = PluginVersion
    )
) {
    private val BotId = 2026338927L
    private val pwd = "QYXW2020"
    private var cacheMessage = Collections.synchronizedMap(mutableMapOf<Int, MutableSet<MessageReceipt<Group>>>())
    private var date = SimpleDateFormat("yyyy-MM-dd").format(Date())


    @ConsoleExperimentalApi
    private val thisBot = MiraiConsole.addBot(BotId, pwd) {
        fileBasedDeviceInfo()
    }


    @ConsoleExperimentalApi
    override fun onEnable() {
        logger.info { "由权益小窝开发组出品。你的全心，我的权益！" }
        Config.reload()
        CommandRegister.commandRegister()
        ForwardtheMsg()
        Replytempmessage()
        SubcribeRecall()
        autoLogin()
        timeAction()
        Config.botId = BotId
    }

    override fun onDisable() {
        super.onDisable()
        CommandRegister.commandUnregister()
        logger.error("插件卸载!")
    }
    /**
     *自动登录模块
     *在[onEnable]时调用，可以自动登录机器人
     */
    @ConsoleExperimentalApi
    fun autoLogin() {
        PluginMain.launch {
            thisBot.alsoLogin()
        }
    }

    /**
     * 监听失物招领管理员群消息模块，可以在监听到新的消息后自动转发到失物招领群里边，其中：
     * message.forEachContent为遍历所有消息内容[MessageContent]（有关消息元[MessageMetadata]与消息内容[MessageContent]
     * 的区别与关系请见文档）
     * 字符串处理将消息链[MessageChain]中的转发关键字符“#”删除后整体转发
     * 若检测到#recall关键字，则撤回回复消息[QuoteReply]的消息源[MessageSource]所转发到失物招领群的消息 （见[msgRecall]）
     */

    private fun ForwardtheMsg() {
        subscribeGroupMessages {
            always {
                PluginMain.logger.info("接收到了新的消息！")
                val id: Long = group.id
                val originGroup: Long = Config.originGroup
                //logger.info("id = ${id}, oringinGroup = ${originGroup}")
                if (id == originGroup && sender.id in Config.senderid) {
                    //logger.info("准备发送")
                    val messageChainBuilder = MessageChainBuilder()
                    if (message.contentToString()[0] == '#' && message.contentToString().length > 1) {
                        message.forEachContent {
                            if (it is PlainText) {
                                messageChainBuilder.add(it.content.replaceFirst("#".toRegex(), ""))
                                return@forEachContent
                            }
                            messageChainBuilder.add(it)
                        }
                        send(messageChainBuilder.asMessageChain(), bot, message.id)
                        if (Data.MessageCnt[sender.id] == null) {
                            Data.MessageCnt[sender.id] = 0
                        } else {
                            Data.MessageCnt[sender.id] = Data.MessageCnt[sender.id]!!.plus(1L)
                        }
                        bot.getGroup(originGroup).sendMessage("失物招领已转发！")
                        return@always
                    } else if (message[QuoteReply] != null && message.contentToString().substring(0, "#recall".length) == "#recall") {
                        val msgID = message[QuoteReply]!!.source.id
                        msgRecall(msgID)
                        Data.MessageCnt[sender.id] = Data.MessageCnt[sender.id]!!.minus(1)
                        bot.getGroup(originGroup).sendMessage("失物招领已撤回")
                    }
                }
            }
        }
    }

    /**
     * 自动转发找机器人的临时消息，文档撰写ing
     */

    @ConsoleExperimentalApi
    private fun Replytempmessage() {
        subscribeTempMessages() {
            always {
                PluginMain.logger.info("接收到了一个临时会话")
                val id: Long = sender.id
                if (id in Data.messagecontact.values) {
                    for (iter in Data.messagecontact) {
                        if (iter.value == id) {
                            bot.getFriend(iter.key).sendMessage(message)
                        }
                    }
                } else {
                    var flag = true
                    while (flag) {
                        for (Mem in Config.senderid) {
                            if (Data.messagecontact[Mem] == null) {
                                bot.getFriend(Mem).sendMessage("这是一个新的对话，结束时请输入英文的^结束")
                                bot.getFriend(Mem).sendMessage(message)
                                Data.messagecontact[Mem] = id
                                flag = false
                                break
                            }
                        }
                        if (flag)
                            delay(3000L)
                    }
                }
            }
        }
        /**
         * 自动转发管理员回复的临时消息内容，文档撰写ing
         */

        subscribeFriendMessages {
            always {
                if (sender.id in Config.senderid) {
                    if (message.contentToString()[0] == '^') {
                        bot.getFriend(sender.id).sendMessage("已经结束此对话，可以接入下一个同学的失物招领！")
                        Data.messagecontact.remove(sender.id)
                    } else if (Data.messagecontact[sender.id] != null) {
                        Data.messagecontact[sender.id]?.let { it1 -> bot.getContact(it1).sendMessage(message) }
                    }
                }
            }
        }
    }

    /**
     *监听撤回消息（用于两分钟以内的消息）
     */
    private fun SubcribeRecall() {
        subscribeAlways<MessageRecallEvent.GroupRecall> {
            if (authorId in Config.senderid && group.id == Config.originGroup) {
                PluginMain.logger.info("准备撤回群内消息！")
                Data.MessageCnt[authorId] = Data.MessageCnt[authorId]!!.minus(1)
                msgRecall(messageId)
            }
        }
    }

    /**
     * 将记录在[cacheMessage]内的消息回执[MessageReceipt]逐个执行[MessageReceipt.recall]，
     * 从而撤回所有失物招领群内的本条失物招领
     */
    private suspend fun msgRecall(messageID: Int) {
        var recallmessage = cacheMessage[messageID]
        while (recallmessage == null || recallmessage.size != Config.groups.size) {
            delay(1000L)
            recallmessage = cacheMessage[messageID]
        }
        for (msg in recallmessage) {
            launch {
                val time: Long = (2L..15000L).random()
                delay(time)
                msg.recall()
            }
        }
    }

    /**
     * 将失物招领由管理员群[Config.originGroup]转发到失物招领群[Config.groups]内
     */
    private fun send(messagechain: MessageChain, bot: Bot, messageID: Int) {
        val groups = Config.groups
        val cnt = AtomicInteger(0)
        val cacheReceipt = Collections.synchronizedSet(mutableSetOf<MessageReceipt<Group>>())
        for (id: Long in groups) {
            launch {
                val time: Long = (2L..15000L).random()
                delay(time)
                cnt.incrementAndGet()
                cacheReceipt.add(bot.getGroup(id).sendMessage(messagechain))
                if (cnt.toInt() == Config.groups.size) {
                    cacheMessage[messageID] = cacheReceipt
                }
            }
        }
    }

    /**
     * 每日清理消息回执[MessageReceipt]以及劳模统计
     */
    @ConsoleExperimentalApi
    private fun timeAction() {
        launch {
            while (true) {
                if (date == SimpleDateFormat("yyyy-MM-dd").format((Date()))) {
                    delay(600000L)
                    continue
                }
                date = SimpleDateFormat("yyyy-MM-dd").format(Date())
                thisBot.getGroup(Config.originGroup).sendMessage("将开始清理撤回列表以及统计劳模")
                delay(10000L)
                cacheMessage.clear()
                thisBot.getGroup(Config.originGroup).sendMessage("撤回列表清理完毕,小窝将无法撤回之前的消息")
                delay(4000L)
                val tot = mutableMapOf<Long, Long>()
                var flag = 0L
                for(it in Data.MessageCnt) {
                    if (it.value > flag) {
                        flag = it.value
                        tot.clear()
                        tot[it.key] = it.value
                    } else if(it.value == flag) {
                        tot[it.key] = it.value
                    }

                }
                thisBot.getGroup(Config.originGroup).sendMessage(
                    "今日的劳模是:"
                )
                for(it in tot) {
                    val sender = thisBot.getFriend(it.key)
                    thisBot.getGroup(Config.originGroup).sendMessage("${sender.nameCardOrNick}, 条数共${it.value}条")
                }
                Data.MessageCnt.clear()
            }
        }
    }
}

object Config : AutoSavePluginConfig("Groups") {
    /**
     * 失物招领管理员群[groups]
     */
    var groups: MutableSet<Long> by value(mutableSetOf<Long>())

    /**
     * 失物招领管理员[senderid]
     */
    var senderid: MutableSet<Long> by value(mutableSetOf<Long>())

    /**
     * 失物招领管理员群[originGroup]
     */
    var originGroup: Long by value(445786154L)
    var botId: Long by value(2026338927L)
}

object Data : AutoSavePluginData("bot") {
    /**
     * 转发的失物招领条数[MessageCnt]
     */
    var MessageCnt by value(mutableMapOf<Long, Long>())

    /**
     * 失物招领对话记录[messagecontact]
     */
    var messagecontact by value(mutableMapOf<Long, Long>())
}
