package com.windf.study.io.nio.chat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class Client {
    private static String USER_CONTENT_SPILIT = "#@#";
    private static String USER_EXIST = "系统提示：该昵称已经存在，请换一个昵称";

    private final InetSocketAddress socketAddress = new InetSocketAddress("localhost", 6789);
    private Charset charset = Charset.forName("UTF-8");
    private Selector selector;
    private SocketChannel client;
    private String nickName = "";

    public Client() throws IOException {
        selector = Selector.open();
        // 打开客户端，监听端口
        client = SocketChannel.open(socketAddress);
        client.configureBlocking(false);
        // 注册读取事件
        client.register(selector, SelectionKey.OP_READ);
    }

    public void session() {
        new Reader().start();
        new Writer().start();
    }

    private class Reader extends Thread {
        @Override
        public void run() {
            try {
                while (true) {
                    int readyChannels = 0;
                    readyChannels = selector.select();
                    if (readyChannels == 0) {
                        continue;
                    }
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        iterator.remove();
                        process(selectionKey);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void process(SelectionKey selectionKey) throws IOException {
            if (selectionKey.isReadable()) {
                SocketChannel channel = (SocketChannel) selectionKey.channel();

                // 读取发送过来的内容
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                StringBuffer content = new StringBuffer();
                while (channel.read(byteBuffer) > 0) {
                    byteBuffer.flip();
                    content.append(charset.decode(byteBuffer));
                }
                // 判断消息
                if (USER_EXIST.equals(content))  {
                    nickName = "";
                }
                // 处理消息
                System.out.println(content);
                // 重置key
                selectionKey.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private class Writer extends Thread {
        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            try {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if ("".equals(line)) {
                        continue;
                    }
                    // 组件message，如果起起名事件
                    String message;
                    if ("".equals(nickName)) {
                        nickName = line;
                        message = nickName + USER_CONTENT_SPILIT;
                    } else {
                        message = nickName + USER_CONTENT_SPILIT + line;
                    }
                    client.write(charset.encode(message));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                scanner.close();
            }
        }
    }

    public static void main(String[] args) {
        try {
            new Client().session();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
