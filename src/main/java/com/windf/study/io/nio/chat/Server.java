package com.windf.study.io.nio.chat;

import javax.naming.ldap.SortKey;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Server {

    //相当于自定义协议格式，与客户端协商好
    private static String USER_CONTENT_SPILIT = "#@#";
    private static String USER_EXIST = "系统提示：该昵称已经存在，请换一个昵称";
    //用来记录在线人数，以及昵称
    private static Set<String> users = new HashSet<String>();
    private int port;
    private Charset charset = Charset.forName("UTF-8");
    private Selector selector;

    public Server(int port) throws IOException {
        this.port = port;

        this.start(port);
    }

    /**
     * 启动程序，绑定端口
     * @param port
     * @throws IOException
     */
    private void start(int port) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        server.configureBlocking(false);

        this.selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("服务已经启动，监听在端口：" + port);
    }

    /**
     * 监听
     * @throws IOException
     */
    private void listen() throws IOException {
        while (true) {
            int wait = selector.select();
            if (wait == 0) {
                continue; // 没有准备好
            }

            // 获取可用的通道集合
            Set<SelectionKey> keys = selector.selectedKeys();

            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                process(selectionKey);
            }
        }
    }

    /**
     * 处理请求
     * @param selectionKey
     */
    private void process(SelectionKey selectionKey) throws IOException {
        // 处理来自客户端的连接请求
        if (selectionKey.isAcceptable()) {
            // 获取客户端的请求
            ServerSocketChannel server = (ServerSocketChannel) selectionKey.channel();
            SocketChannel client = server.accept();
            // 设置为非阻塞模式
            client.configureBlocking(false);
            // 将新获取的客户端请求channel注册在selector上，注册读取事件
            client.register(this.selector, SelectionKey.OP_READ);
            // 将现在的key，设置为准备接受其他请求
            selectionKey.interestOps(SelectionKey.OP_ACCEPT);
            // 向客户端发送请求？现在就可以发送了？
            client.write(charset.encode("请输入您的昵称"));
        }

        // 处理来自客户端的数据读取请求
        if (selectionKey.isReadable()) {
            // 获取客户端对应的通道
            SocketChannel client = (SocketChannel) selectionKey.channel();
            // 读取数据
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            StringBuffer content = new StringBuffer();
            try {
                while (client.read(byteBuffer) > 0) {
                    byteBuffer.flip();
                    content.append(charset.decode(byteBuffer));
                }
                // 将连接重置，准备接受下一次请求
                selectionKey.interestOps(SelectionKey.OP_READ);
            } catch (IOException e) {
                // 发生异常，关闭通道
                selectionKey.cancel();
                if (selectionKey.channel() != null) {
                    selectionKey.channel().close();
                }
            }
            // 处理数据
            if (content.length() > 0) {
                String[] contentArray = content.toString().split(USER_CONTENT_SPILIT);
                // 注册用户的情况
                if (contentArray != null && contentArray.length == 1) {
                    String nickName = contentArray[0];
                    // 已经注册的步伐了
                    if (users.contains(nickName)) {
                        client.write(charset.encode(USER_EXIST));
                    }
                    // 没有注册的，发送广播消息
                    else {
                        users.add(nickName);
                        int onlineCount = onlineCount();
                        String message = "欢迎[" + nickName + "]进入聊天室！当前在线人数：" + onlineCount + "人";
                        broadCast(null, message);
                    }
                }
                // 发送消息的情况
                else if (contentArray != null && contentArray.length > 1) {
                    // 组织消息
                    String nickName = contentArray[0];
                    String message = content.substring(nickName.length() + USER_CONTENT_SPILIT.length());
                    message = "[" + nickName + "]说：" + message;
                    // 广播消息
                    broadCast(client, message);
                }
            }
        }
    }

    /**
     * 广播消息到所有人
     * @param client    不给谁发
     * @param message
     */
    private void broadCast(SocketChannel client, String message) throws IOException {
        for (SelectionKey key : selector.keys()) {
            Channel targetClient = key.channel();
            if (targetClient instanceof SocketChannel) {
                if (targetClient != client)
                    ((SocketChannel) targetClient).write(charset.encode(message));{
                }
            }
        }
    }

    /**
     * 统计当前的链接数
     * @return
     */
    private int onlineCount() {
        int count = 0;
        for (SelectionKey key : selector.keys()) {
            Channel target = key.channel();
            if (target instanceof SocketChannel) {
                count ++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        try {
            new Server(6789).listen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
