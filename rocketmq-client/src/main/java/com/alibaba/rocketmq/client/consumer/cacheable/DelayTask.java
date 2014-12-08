package com.alibaba.rocketmq.client.consumer.cacheable;

import com.alibaba.rocketmq.client.producer.concurrent.DefaultLocalMessageStore;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.common.message.MessageExt;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class DelayTask implements Runnable {
    private final MessageHandler messageHandler;

    private DefaultLocalMessageStore localMessageStore;

    public DelayTask(MessageHandler messageHandler,
                     DefaultLocalMessageStore localMessageStore) {
        this.localMessageStore = localMessageStore;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        Message[] messages = localMessageStore.pop();
        TreeMap<String, MessageExt> messageSet = getMessageTree(messages);

        Set<Map.Entry<String, MessageExt>> set = messageSet.entrySet();

        for (Map.Entry<String, MessageExt> entry : set) {
            MessageExt me = entry.getValue();
            Message mes = TranslateMsg.getMessageFromMessageExt(me);

            if (Long.parseLong(me.getProperty("next_time")) - System.currentTimeMillis() < 1000) {
                int result = messageHandler.handle(me);
                if (result > 0) {
                    mes.putUserProperty("next_time", String.valueOf(System.currentTimeMillis() + result));
                    localMessageStore.stash(mes);
                }
            } else {
                localMessageStore.stash(mes);
            }
        }
    }

    private TreeMap<String, MessageExt> getMessageTree(Message[] messages) {
        TreeMap<String, MessageExt> messageTreeMap = new TreeMap<String, MessageExt>();

        for (final Message message : messages) {
            if (null == message)
                continue;
            MessageExt me = TranslateMsg.getMessageExtFromMessage(message);
            me.putUserProperty("next_time", message.getProperty("next_time"));
            messageTreeMap.put(me.getMsgId(), me);
        }

        return messageTreeMap;
    }
}